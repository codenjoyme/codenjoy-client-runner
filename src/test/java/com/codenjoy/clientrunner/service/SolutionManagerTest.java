package com.codenjoy.clientrunner.service;

import com.codenjoy.clientrunner.TokenTest;
import com.codenjoy.clientrunner.config.DockerConfig;
import com.codenjoy.clientrunner.dto.SolutionSummary;
import com.codenjoy.clientrunner.exception.SolutionNotFoundException;
import com.codenjoy.clientrunner.model.Solution;
import com.codenjoy.clientrunner.model.Token;
import com.codenjoy.clientrunner.service.facade.DockerService;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static com.codenjoy.clientrunner.model.Solution.Status.ERROR;
import static com.codenjoy.clientrunner.model.Solution.Status.KILLED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

@SpringBootTest
@TestExecutionListeners(MockitoTestExecutionListener.class)
public class SolutionManagerTest extends AbstractTestNGSpringContextTests {

    @MockBean
    private DockerService dockerService;

    @SpyBean
    private DockerConfig config;

    @Autowired
    private SolutionManager solutionManager;

    private File sources;
    private Token token;

    @BeforeMethod
    @SneakyThrows
    public void generateJavaSources() {
        Path path = Path.of("./target/testJavaSources-" +
                new Random().nextInt(Integer.MAX_VALUE));
        path = Files.createDirectory(path);
        Files.createFile(path.resolve("pom.xml"));
        this.sources = path.toFile();
    }

    @BeforeMethod
    public void setup() {
        reset(config);
        solutionManager.clear();
        token = TokenTest.generateValidToken();
    }

    @AfterMethod
    public void cleanup() {
        solutionManager.killAll(token);
        reset(dockerService);
    }

    @Test
    public void shouldAddDockerfile_whenRunSolution_withValidTokenAndSources() {
        // when
        solutionManager.runSolution(token, sources);

        // then
        assertTrue(Files.exists(Path.of(sources.getPath(), "Dockerfile")));
    }

    @Test
    public void shouldCallDockerBuildImage_whenRunSolution_withValidTokenAndSources() {
        // when
        solutionManager.runSolution(token, sources);

        // then
        verify(dockerService, only()).buildImage(same(sources), same(token.getServerUrl()), any(), any());
    }

    @Test
    @SneakyThrows
    public void shouldSetErrorSolutionStatus_whenRunSolution_withDockerBuildImageThrowAnException() {
        // given
        doThrow(RuntimeException.class)
                .when(dockerService)
                .buildImage(isA(File.class), anyString(), any(), any());

        // when
        int id = solutionManager.runSolution(token, sources);

        // then
        assertEquals(statusOf(id), ERROR);
    }

    @Test
    public void shouldKillLastSolutionBeforeRunNew_whenRunSolution_withValidTokenAndSources() {
        // given
        int id1 = solutionManager.runSolution(token, sources);
        int id2 = solutionManager.runSolution(token, sources);

        // when
        int id3 = solutionManager.runSolution(token, sources);

        // then
        assertFalse(statusOf(id1).isActive());
        assertFalse(statusOf(id2).isActive());
        assertTrue(statusOf(id3).isActive());
    }

    @Test
   public void shouldDontRunSolution_whenKillItImmediately_afterRunIt() {
        // given
        // simulate multithreading TODO to use synchronized section inside solutionManager
        when(config.getDockerfilesFolder()).thenAnswer(invocation -> {
            kill(token);
            return invocation.callRealMethod();
        });

        // when
        solutionManager.runSolution(token, sources);

        // then
        SolutionSummary summary = solutionManager.getAllSolutionSummary(token).get(0);
        assertEquals(summary.getStatus(), KILLED.name());
        verify(dockerService, never()).buildImage(any(), any(), any(), any());
    }

    private void kill(Token token) {
        SolutionSummary summary = solutionManager.getAllSolutionSummary(token).get(0);
        solutionManager.kill(token, summary.getId());
    }

    @Test
    public void shouldKillTheSolution_whenKill_withExistingSolution() {
        // given
        int id = solutionManager.runSolution(token, sources);

        // when
        solutionManager.kill(token, id);

        // then
        assertFalse(statusOf(id).isActive());
    }

    @Test
    public void shouldThrowAnException_whenKill_withNonExistingSolution() {
        // given
        int id = solutionManager.runSolution(token, sources);
        int nonExistsId = Integer.MAX_VALUE;

        // then
        assertThrows(
                SolutionNotFoundException.class,
                // when
                () -> solutionManager.kill(token, nonExistsId)
        );
        assertTrue(statusOf(id).isActive());
    }

    private Solution.Status statusOf(int solutionId) {
        SolutionSummary solution = solutionManager.getSolutionSummary(token, solutionId);
        return Solution.Status.valueOf(solution.getStatus());
    }
}
