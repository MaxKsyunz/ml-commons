/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.ml.common.transport.sync.MLSyncUpAction;
import org.opensearch.ml.common.transport.sync.MLSyncUpInput;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodeResponse;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesRequest;

@Log4j2
public class MLSyncUpCron implements Runnable {

    private Client client;
    private DiscoveryNodeHelper nodeHelper;

    public MLSyncUpCron(Client client, DiscoveryNodeHelper nodeHelper) {
        this.client = client;
        this.nodeHelper = nodeHelper;
    }

    @Override
    public void run() {
        log.debug("ML sync job starts");
        DiscoveryNode[] allNodes = nodeHelper.getAllNodes();
        MLSyncUpInput gatherInfoInput = MLSyncUpInput.builder().getLoadedModels(true).build();
        MLSyncUpNodesRequest gatherInfoRequest = new MLSyncUpNodesRequest(allNodes, gatherInfoInput);
        // gather running model/tasks on nodes
        client.execute(MLSyncUpAction.INSTANCE, gatherInfoRequest, ActionListener.wrap(r -> {
            List<MLSyncUpNodeResponse> responses = r.getNodes();
            // key is model id, value is set of worker node ids
            Map<String, Set<String>> modelWorkerNodes = new HashMap<>();
            // key is task id, value is set of worker node ids
            Map<String, Set<String>> runningLoadModelTasks = new HashMap<>();
            for (MLSyncUpNodeResponse response : responses) {
                String nodeId = response.getNode().getId();
                String[] loadedModelIds = response.getLoadedModelIds();
                if (loadedModelIds != null && loadedModelIds.length > 0) {
                    for (String modelId : loadedModelIds) {
                        Set<String> workerNodes = modelWorkerNodes.computeIfAbsent(modelId, it -> new HashSet<>());
                        workerNodes.add(nodeId);
                    }
                }
                String[] runningLoadModelTaskIds = response.getRunningLoadModelTaskIds();
                if (runningLoadModelTaskIds != null && runningLoadModelTaskIds.length > 0) {
                    for (String taskId : runningLoadModelTaskIds) {
                        Set<String> workerNodes = runningLoadModelTasks.computeIfAbsent(taskId, it -> new HashSet<>());
                        workerNodes.add(nodeId);
                    }
                }
            }
            for (Map.Entry<String, Set<String>> entry : modelWorkerNodes.entrySet()) {
                log.debug("will sync model worker nodes for model: {}: {}", entry.getKey(), entry.getValue().toArray(new String[0]));
            }
            for (Map.Entry<String, Set<String>> entry : runningLoadModelTasks.entrySet()) {
                log.debug("will sync running task: {}: {}", entry.getKey(), entry.getValue().toArray(new String[0]));
            }
            MLSyncUpInput.MLSyncUpInputBuilder inputBuilder = MLSyncUpInput
                .builder()
                .syncRunningLoadModelTasks(true)
                .runningLoadModelTasks(runningLoadModelTasks);
            if (modelWorkerNodes.size() == 0) {
                log.debug("No loaded model found. Will clear model routing on all nodes");
                inputBuilder.clearRoutingTable(true);
            } else {
                inputBuilder.modelRoutingTable(modelWorkerNodes);
            }
            MLSyncUpInput syncUpInput = inputBuilder.build();
            MLSyncUpNodesRequest syncUpRequest = new MLSyncUpNodesRequest(allNodes, syncUpInput);
            // sync up running model/tasks on nodes
            client
                .execute(
                    MLSyncUpAction.INSTANCE,
                    syncUpRequest,
                    ActionListener
                        .wrap(
                            re -> { log.debug("sync model routing job finished"); },
                            ex -> { log.error("Failed to sync model routing", ex); }
                        )
                );
        }, e -> { log.error("Failed to sync model routing", e); }));
    }
}
