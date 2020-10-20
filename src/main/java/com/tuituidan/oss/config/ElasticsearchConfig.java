package com.tuituidan.oss.config;

import com.alibaba.fastjson.JSON;
import com.tuituidan.oss.exception.ImageHostException;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.config.ElasticsearchConfigurationSupport;
import org.springframework.data.elasticsearch.core.EntityMapper;
import org.springframework.data.elasticsearch.core.ResultsMapper;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.aggregation.impl.AggregatedPageImpl;

/**
 * 实现关键字高亮显示.
 *
 * @author zhujunhan
 * @version 1.0
 * @date 2020/10/20
 */
@Configuration
public class ElasticsearchConfig extends ElasticsearchConfigurationSupport {

    @Bean
    @Override
    public ResultsMapper resultsMapper() {
        return new ResultsMapper() {
            @Override
            public EntityMapper getEntityMapper() {
                return entityMapper();
            }

            @Override
            public <T> AggregatedPage<T> mapResults(SearchResponse response, Class<T> clazz, Pageable pageable) {
                SearchHit[] hits = response.getHits().getHits();
                List<T> resultList = new ArrayList<>();
                if (ArrayUtils.isEmpty(hits)) {
                    return new AggregatedPageImpl<>(resultList, pageable, response.getHits().getTotalHits());
                }
                //解析出关键字设为高亮文本，放到结果集中
                for (SearchHit searchHit : hits) {
                    try {
                        T fileDoc = JSON.parseObject(searchHit.getSourceAsString(), clazz);
                        highlightFields(fileDoc, searchHit.getHighlightFields());
                        resultList.add(fileDoc);
                    } catch (Exception e) {
                        throw ImageHostException.builder().error("标签高亮显示失败", e).build();
                    }
                }
                return new AggregatedPageImpl<>(resultList, pageable, response.getHits().getTotalHits());
            }


            @Override
            public <T> List<T> mapResults(MultiGetResponse responses, Class<T> clazz) {
                return Collections.emptyList();
            }

            @Override
            public <T> T mapResult(GetResponse response, Class<T> clazz) {
                return null;
            }

            private <T> void highlightFields(T fileDoc, Map<String, HighlightField> highlightFields) throws
                    InvocationTargetException,
                    IllegalAccessException {
                if (MapUtils.isEmpty(highlightFields)) {
                    return;
                }
                for (Map.Entry<String, HighlightField> item : highlightFields.entrySet()) {
                    BeanUtils.setProperty(fileDoc, item.getKey(), item.getValue().fragments()[0].toString());
                }
            }
        };
    }
}
