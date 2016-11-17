/*
 * Copyright (c) 2016-present, Daniel Lo Nigro (Daniel15)
 * All rights reserved.
 *
 * This source code is licensed under the MIT-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.jenkinsci.plugins.githubissues;

import hudson.model.BuildBadgeAction;
import hudson.model.Result;
import org.jenkinsci.plugins.github.util.XSSApi;
import org.kohsuke.github.GHIssue;

public class GitHubIssueAction implements BuildBadgeAction {

    private int issueNumber;
    private final String issueUrl;
    private TransitionAction action;
    enum TransitionAction {
        CLOSE, OPEN , REOPEN, CONTINUE
    }

    public GitHubIssueAction(GHIssue issue, TransitionAction action) {
        this.issueNumber = issue.getNumber();
        this.issueUrl = XSSApi.asValidHref(issue.getHtmlUrl().toString());
        this.action = action;
    }

    @Override
    public String getDisplayName() {
        return "GitHub Issue";
    }

    @Override
    public String getIconFileName() {
        return "/plugin/github-issues/img/issue-opened.svg";
    }

    @Override
    public String getUrlName() {
        return XSSApi.asValidHref(issueUrl);
    }

    public int getIssueNumber() {
        return issueNumber;
    }

    public boolean isOpened() { return action == TransitionAction.OPEN; }

    public boolean isClosed() { return action == TransitionAction.CLOSE; }

    public boolean isContinued() { return action == TransitionAction.CONTINUE; }

    public boolean isReopened() { return action == TransitionAction.REOPEN; }

    public String getIssueUrl() {
        return issueUrl;
    }
}

