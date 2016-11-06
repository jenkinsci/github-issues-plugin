/**
 * Copyright (c) 2016-present, Daniel Lo Nigro (Daniel15)
 * All rights reserved.
 *
 * This source code is licensed under the MIT-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.jenkinsci.plugins.githubissues;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.IOException;

@XStreamAlias("github-issues")
@ExportedBean
public class GitHubIssueJobProperty extends JobProperty<Job<?, ?>> {
    private int issueNumber;

    @DataBoundConstructor
    public GitHubIssueJobProperty(int issueNumber) {
        this.issueNumber = issueNumber;
    }

    public int getIssueNumber() {
        return issueNumber;
    }

    public void setIssueNumber(int issueNumber) {
        this.issueNumber = issueNumber;
    }

    /**
     * Gets the {@link GitHubIssueJobProperty} for the specified job. If the job does not yet have this property,
     * creates a new instance
     *
     * @param job Jenkins jobs to get the GitHubIssueJobProperty for
     * @return The GitHubIssueJobProperty
     * @throws IOException If there is an error getting the property
     */
    public static GitHubIssueJobProperty getOrCreateForJob(Job<?, ?> job) throws IOException {
        GitHubIssueJobProperty property = job.getProperty(GitHubIssueJobProperty.class);
        if (property == null) {
            property = new GitHubIssueJobProperty(0);
            job.addProperty(property);
        }
        return property;
    }

    /**
     * Descriptor for the {@link GitHubIssueJobProperty}.
     */
    @Extension
    public static class GitHubIssueJobPropertyDescriptor extends JobPropertyDescriptor {

        @Override
        public String getDisplayName() {
            return "GitHub Issue Configuration";
        }
    }
}
