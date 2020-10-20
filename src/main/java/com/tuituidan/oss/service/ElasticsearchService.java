package com.tuituidan.oss.service;

import com.tuituidan.oss.bean.FileDoc;
import com.tuituidan.oss.bean.FileInfo;
import com.tuituidan.oss.bean.FileQuery;
import com.tuituidan.oss.consts.Consts;
import com.tuituidan.oss.kit.BeanKit;
import com.tuituidan.oss.kit.ThreadPoolKit;
import com.tuituidan.oss.repository.FileDocRepository;

import java.util.Date;
import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

/**
 * ElasticsearchService.
 *
 * @author zhujunhan
 * @version 1.0
 * @date 2020/10/14
 */
@Service
public class ElasticsearchService {

    @Resource
    private MinioService minioService;

    @Resource
    private FileDocRepository fileDocRepository;

    @Resource
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Resource
    private FileCacheService fileCacheService;

    @PostConstruct
    private void init() {
        if (elasticsearchRestTemplate.indexExists(Consts.ES_INDEX)) {
            fileDocRepository.findAll().forEach(item -> {
                if (StringUtils.isNotBlank(item.getMd5())) {
                    fileCacheService.put(item.getMd5(), item.getPath());
                }
            });
        }
    }

    /**
     * 异步存文件信息.
     *
     * @param objName  objName
     * @param md5      md5
     * @param fileInfo fileInfo
     */
    public void asyncSaveFileDoc(String objName, String md5, FileInfo fileInfo) {
        ThreadPoolKit.execute(() -> {
            FileDoc fileDoc = BeanKit.convert(fileInfo, FileDoc.class);
            fileDoc.setPath(objName);
            fileDoc.setMd5(md5);
            fileDoc.setCreateDate(new Date());
            fileDocRepository.save(fileDoc);
        });
    }

    /**
     * 根据标签查询文件.
     *
     * @param fileQuery 查询参数
     * @return List
     */
    public Page<FileDoc> search(FileQuery fileQuery) {
        // 拼接查询参数
        NativeSearchQueryBuilder searchQueryBuilder = new NativeSearchQueryBuilder()
                .withHighlightFields()
                // 分页查询
                .withPageable(PageRequest.of(fileQuery.getPageIndex(), fileQuery.getPageSize()))
                // 排序
                .withSort(SortBuilders.scoreSort());
        if (StringUtils.isNotBlank(fileQuery.getTags())) {
            // 高亮标签显示
            searchQueryBuilder.withHighlightFields(new HighlightBuilder.Field("tags")
                    .preTags("<span style=\"color:red\">").postTags("</span>"));
            searchQueryBuilder.withQuery(QueryBuilders.matchQuery("tags", fileQuery.getTags()))
                    .withSort(SortBuilders.scoreSort());
        } else {
            searchQueryBuilder.withSort(SortBuilders.fieldSort("createDate").order(SortOrder.DESC));
        }
        Page<FileDoc> fileDocs = elasticsearchRestTemplate.queryForPage(searchQueryBuilder.build(), FileDoc.class);
        if (CollectionUtils.isNotEmpty(fileDocs.getContent())) {
            fileDocs.getContent().forEach(item -> item.setPath(minioService.getObjectUrl(item.getPath())));
        }
        return fileDocs;
    }

    /**
     * delete.
     *
     * @param id 需要删除的文件id
     */
    public void delete(String id) {
        Optional<FileDoc> fileDoc = fileDocRepository.findById(id);
        if (fileDoc.isPresent()) {
            fileDocRepository.deleteById(id);
            minioService.deleteObject(fileDoc.get().getPath());
        }
    }

}
