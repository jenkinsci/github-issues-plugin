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
     * @throws IOException
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
     * @param notifier The instance of GitHubIssueNotifier
     * @param repo Repository to create the issue in
     * @param listener Build listener
     * @param workspace Build workspace
     * @return The issue that was created
     * @throws IOException If creating the issue fails
     */
    public static GHIssue createIssue(
        Run<?, ?> run,
        GitHubIssueNotifier notifier,
        GHRepository repo,
        TaskListener listener,
        FilePath workspace
    ) throws IOException {
        GitHubIssueNotifier.DescriptorImpl descriptor = notifier.getDescriptor();
        String title = StringUtils.defaultIfBlank(notifier.getCustomTitle(), descriptor.getIssueTitle());
        String body = StringUtils.defaultIfBlank(notifier.getCustomBody(), descriptor.getIssueBody());
        String label = StringUtils.defaultIfBlank(notifier.getCustomLabel(), descriptor.getIssueLabel());

        GHIssueBuilder issue = repo.createIssue(formatText(title, run, listener, workspace))
            .body(formatText(body, run, listener, workspace));

        if (label != null && !label.isEmpty()) {
            issue = issue.label(label);
        }
        return issue.create();
    }
}
