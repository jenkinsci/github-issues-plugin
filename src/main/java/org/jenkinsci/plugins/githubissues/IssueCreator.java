/**
 * Copyright (c) 2016-present, Daniel Lo Nigro (Daniel15)
 * All rights reserved.
 *
 * This source code is licensed under the MIT-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.jenkinsci.plugins.githubissues;

import hudson.model.Job;
import hudson.model.Run;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueBuilder;
import org.kohsuke.github.GHRepository;

import java.io.IOException;

/**
 * Handles creating GitHub issues.
 */
public abstract class IssueCreator {
    /**
     * Number of lines of build output to display in the GitHub issue.
     */
    private static final int LOG_LINES_TO_DISPLAY = 50;

    /**
     * Formats text for a GitHub issue, replacing placeholders like {NAME} and {URL}.
     * @param text Text to format
     * @param run The build run
     * @return Formatted text
     * @throws IOException
     */
    public static String formatText(String text, Run<?, ?> run) throws IOException {
        Job<?, ?> job = run.getParent();
        return text
            .replace("{NAME}", job.getName())
            .replace("{OUTPUT}", StringUtils.join(run.getLog(LOG_LINES_TO_DISPLAY), "\n"))
            .replace("{URL}", run.getAbsoluteUrl());
    }

    /**
     * Creates a GitHub issue for a failing build
     * @param run Build that failed
     * @param descriptor Descriptor for GitHubIssueNotifier
     * @param repo Repository to create the issue in
     * @return The issue that was created
     * @throws IOException
     */
    public static GHIssue createIssue(
        Run<?, ?> run,
        GitHubIssueNotifier.DescriptorImpl descriptor,
        GHRepository repo
    ) throws IOException {
        GHIssueBuilder issue = repo.createIssue(formatText(descriptor.getIssueTitle(), run))
            .body(formatText(descriptor.getIssueBody(), run));

        String issueLabel = descriptor.getIssueLabel();
        if (issueLabel != null && !issueLabel.isEmpty()) {
            issue = issue.label(issueLabel);
        }
        return issue.create();
    }
}
