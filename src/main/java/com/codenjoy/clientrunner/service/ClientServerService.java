package com.codenjoy.clientrunner.service;

import com.codenjoy.clientrunner.config.ClientServerServiceConfig;
import com.codenjoy.clientrunner.dto.CheckRequest;
import com.codenjoy.clientrunner.dto.SolutionSummary;
import com.codenjoy.clientrunner.model.Server;
import lombok.AllArgsConstructor;
import org.eclipse.jgit.api.Git;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@AllArgsConstructor
public class ClientServerService {

    private final ClientServerServiceConfig config;
    private final GitService git;
    private final DockerRunnerService docker;

    public void checkSolution(CheckRequest checkRequest) {
        Server server = extractPlayerIdAndCode(checkRequest.getServer());

        File directory = new File(String.format("./%s/%s/%s/%s",
                config.getSolutionFolder().getPath(),
                server.getPlayerId(), server.getCode(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern(config.getSolutionFolder().getPattern()))));

        // TODO: async
        Git repo = git.clone(checkRequest.getRepo(), directory);

        if (repo == null) {
            throw new IllegalArgumentException("Can not clone repository: " +
                    checkRequest.getRepo());
        }

        docker.runSolution(directory,
                server.getPlayerId(), server.getCode(),
                checkRequest.getServer());
    }

    private Server extractPlayerIdAndCode(String url) {
        return new Server(url, config.getServerRegex());
    }

    public void killSolution(String server, int solutionId) {
        Server playerIdAndCode = extractPlayerIdAndCode(server);
        docker.kill(playerIdAndCode.getPlayerId(), playerIdAndCode.getCode(), solutionId);
    }

    public List<SolutionSummary> getAllSolutionsSummary(String server) {
        Server playerIdAndCode = extractPlayerIdAndCode(server);
        return docker.getAllSolutionsSummary(playerIdAndCode.getPlayerId(), playerIdAndCode.getCode());
    }

    public SolutionSummary getSolutionSummary(String server, int solutionId) {
        Server playerIdAndCode = extractPlayerIdAndCode(server);
        return docker.getSolutionSummary(solutionId, playerIdAndCode.getPlayerId(), playerIdAndCode.getCode());
    }

    public List<String> getBuildLogs(String server, int solutionId, int offset) {
        Server playerIdAndCode = extractPlayerIdAndCode(server);
        return docker.getBuildLogs(solutionId, playerIdAndCode.getPlayerId(), playerIdAndCode.getCode(), offset);
    }

    public List<String> getRuntimeLogs(String server, int solutionId, int offset) {
        Server playerIdAndCode = extractPlayerIdAndCode(server);
        return docker.getRuntimeLogs(solutionId, playerIdAndCode.getPlayerId(), playerIdAndCode.getCode(), offset);
    }

}
