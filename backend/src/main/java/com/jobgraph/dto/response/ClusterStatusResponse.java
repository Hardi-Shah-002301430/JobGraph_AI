package com.jobgraph.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data @Builder
public class ClusterStatusResponse {
    private int totalNodes;
    private int upNodes;
    private String selfAddress;
    private String leaderAddress;
    private List<NodeInfo> nodes;

    @Data @Builder
    public static class NodeInfo {
        private String address;
        private String status;  // Up, Joining, Leaving, Down, Removed
        private List<String> roles;
    }
}
