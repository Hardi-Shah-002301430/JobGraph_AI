package com.jobgraph.controller;

import com.jobgraph.cluster.ClusterManager;
import com.jobgraph.dto.response.ClusterStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cluster")
@RequiredArgsConstructor
public class ClusterController {

    private final ClusterManager clusterManager;

    @GetMapping("/status")
    public ClusterStatusResponse status() {
        return clusterManager.getClusterStatus();
    }
}
