package com.chaosinjector.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chaosinjector.api.dto.ConnectRequest;
import com.chaosinjector.api.dto.ConnectResponse;
import com.chaosinjector.api.dto.ContainerDto;
import com.chaosinjector.target.ConnectionRegistry;

/** Daemon connection + container listing (spec §8, §10). */
@RestController
@RequestMapping("/api/targets")
public class TargetController {

    private final ConnectionRegistry connections;

    public TargetController(ConnectionRegistry connections) {
        this.connections = connections;
    }

    @PostMapping("/connect")
    public ConnectResponse connect(@RequestBody(required = false) ConnectRequest req) {
        ConnectRequest r = req == null ? new ConnectRequest(null, null, false) : req;
        String id = connections.connect(r.host(), r.certPath(), r.tlsVerify());
        return new ConnectResponse(id);
    }

    @GetMapping("/{connectionId}/containers")
    public List<ContainerDto> containers(@PathVariable String connectionId) {
        return connections.resolve(connectionId).listWorkloads().stream()
                .map(ContainerDto::from)
                .toList();
    }
}
