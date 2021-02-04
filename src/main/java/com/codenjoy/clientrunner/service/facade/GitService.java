package com.codenjoy.clientrunner.service.facade;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;

import java.io.File;

@Slf4j
@Service
@AllArgsConstructor
public class GitService {

    public Git clone(String repoURI, File directory) {
        try {
            return Git.cloneRepository()
                    .setURI(repoURI)
                    .setDirectory(directory)
                    .call();
        } catch (GitAPIException e) {
            log.error("Can not clone repository: {}", repoURI);
            return null;
        }
    }
}