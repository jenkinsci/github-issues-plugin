/**
 * Copyright (c) 2016-present, Daniel Lo Nigro (Daniel15)
 * All rights reserved.
 *
 * This source code is licensed under the MIT-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.jenkinsci.plugins.githubissues;

import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueBuilder;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Handles creating GitHub issues.
 */
public abstract class IssueCreator {
    private static ArrayList<TokenMacro> privateTokens;

    /**
     * Formats text for a GitHub issue, replacing placeholders like {NAME} and {URL}.
     * @param text Text to format
     * @param run The build run
     * @param listener Build listener
     * @param workspace Build workspace
     * @return Formatted text
     * @throws IOException when an unexpected problem occurs
     */
    public static String formatText(
        String text,
        Run<?, ?> run,
        TaskListener listener,
        FilePath workspace
    ) throws IOException {
        if (privateTokens == null) {
            ArrayList<TokenMacro> newPrivateTokens = new ArrayList<>();
            newPrivateTokens.add(new OutputTokenMacro());
            privateTokens = newPrivateTokens;
        }

        try {
            return TokenMacro.expandAll(run, workspace, listener, text, true, privateTokens);
        } catch (Exception e) {
            listener.error("Unable to expand tokens: " + e.getMessage());
            return text;
        }
    }

    /**
     * Creates a GitHub issue for a failing build
     * @param run Build that failed
     * @param jobConfig the job config of the GitHubIssueNotifier
     * @param repo Repository to create the issue in
     * @param listener Build listener
     * @param workspace Build workspace
     * @return The issue that was created
     * @throws IOException when an unexpected problem occurs
     */
    public static GHIssue createIssue(
        Run<?, ?> run,
        GitHubIssueNotifier jobConfig,
        GHRepository repo,
        TaskListener listener,
        FilePath workspace
    ) throws IOException {
        final GitHubIssueNotifier.DescriptorImpl globalConfig = jobConfig.getDescriptor();

        String title = StringUtils.defaultIfBlank(jobConfig.getIssueTitle(), globalConfig.getIssueTitle());
        String body = StringUtils.defaultIfBlank(jobConfig.getIssueBody(), globalConfig.getIssueBody());
        String label = StringUtils.defaultIfBlank(jobConfig.getIssueLabel(), globalConfig.getIssueLabel());

        GHIssueBuilder issueBuilder = repo.createIssue(formatText(title, run, listener, workspace))
            .body(formatText(body, run, listener, workspace));

        GHIssue issue = issueBuilder.create();

        if (label != null && !label.isEmpty()) {
            issue.setLabels(label.split(",| "));
        }
        return issue;
    }
}
