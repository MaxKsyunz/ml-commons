/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.junit.After;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.clustering.KMeansParams;
import org.opensearch.ml.stats.ActionName;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.search.builder.SearchSourceBuilder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class RestMLTrainAndPredictIT extends MLCommonsRestTestCase {
    private String irisIndex = "iris_data_train_predict_it";

    @Before
    public void setup() throws IOException, ParseException {
        ingestIrisData(irisIndex);
    }

    @After
    public void deleteIndices() throws IOException {
        deleteIndexWithAdminClient(irisIndex);
    }

    public void testTrainAndPredictKmeans() throws IOException {
        validateStats(FunctionName.KMEANS, ActionName.TRAIN_PREDICT, 0, 0, 0, 0);
        trainAndPredictKmeansWithCustomParam();
        validateStats(FunctionName.KMEANS, ActionName.TRAIN_PREDICT, 0, 0, 1, 1);

        // train with empty parameters
        trainAndPredictKmeansWithEmptyParam();
        validateStats(FunctionName.KMEANS, ActionName.TRAIN_PREDICT, 0, 0, 2, 2);
    }

    private void trainAndPredictKmeansWithCustomParam() throws IOException {
        KMeansParams params = KMeansParams.builder().centroids(3).build();
        trainAndPredictKmeansWithParmas(params, clusterCount -> assertTrue(clusterCount.size() >= 2));
    }

    private void trainAndPredictKmeansWithEmptyParam() throws IOException {
        KMeansParams params = KMeansParams.builder().build();
        trainAndPredictKmeansWithParmas(params, clusterCount -> assertEquals(2, clusterCount.size()));
    }

    private void trainAndPredictKmeansWithParmas(KMeansParams params, Consumer<Map<Double, Integer>> function) throws IOException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(new MatchAllQueryBuilder());
        sourceBuilder.size(1000);
        sourceBuilder.fetchSource(new String[] { "petal_length_in_cm", "petal_width_in_cm" }, null);
        MLInputDataset inputData = SearchQueryInputDataset
            .builder()
            .indices(ImmutableList.of(irisIndex))
            .searchSourceBuilder(sourceBuilder)
            .build();
        trainAndPredictKmeansWithIrisData(params, inputData, function);
    }

    private void trainAndPredictKmeansWithIrisData(KMeansParams params, MLInputDataset inputData, Consumer<Map<Double, Integer>> function)
        throws IOException {
        MLInput kmeansInput = MLInput.builder().algorithm(FunctionName.KMEANS).parameters(params).inputDataset(inputData).build();
        Response kmeansResponse = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/_train_predict/kmeans",
                ImmutableMap.of(),
                TestHelper.toHttpEntity(kmeansInput),
                null
            );
        HttpEntity entity = kmeansResponse.getEntity();
        assertNotNull(kmeansResponse);
        String entityString = TestHelper.httpEntityToString(entity);
        Map map = gson.fromJson(entityString, Map.class);
        Map predictionResult = (Map) map.get("prediction_result");
        ArrayList rows = (ArrayList) predictionResult.get("rows");
        Map<Double, Integer> clusterCount = new HashMap<>();
        for (Object obj : rows) {
            Double value = (Double) ((Map) ((ArrayList) ((Map) obj).get("values")).get(0)).get("value");
            if (!clusterCount.containsKey(value)) {
                clusterCount.put(value, 1);
            } else {
                Integer count = clusterCount.get(value);
                clusterCount.put(value, ++count);
            }
        }
        function.accept(clusterCount);
    }
}
