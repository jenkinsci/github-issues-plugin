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
     * @return Formatted text
     * @throws IOException
     */
    public static String formatText(
        String text,
        Run<?, ?> run,
        TaskListener listener,
        FilePath workspace
    ) throws IOException {
        if (privateTokens == null) {
            privateTokens = new ArrayList<>();
            privateTokens.add(new OutputTokenMacro());
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
     * @param descriptor Descriptor for GitHubIssueNotifier
     * @param repo Repository to create the issue in
     * @return The issue that was created
     * @throws IOException
     */
    public static GHIssue createIssue(
        Run<?, ?> run,
        GitHubIssueNotifier.DescriptorImpl descriptor,
        GHRepository repo,
        TaskListener listener,
        FilePath workspace
    ) throws IOException {
        GHIssueBuilder issue = repo.createIssue(formatText(descriptor.getIssueTitle(), run, listener, workspace))
            .body(formatText(descriptor.getIssueBody(), run, listener, workspace));

        String issueLabel = descriptor.getIssueLabel();
        if (issueLabel != null && !issueLabel.isEmpty()) {
            issue = issue.label(issueLabel);
        }
        return issue.create();
    }
}
