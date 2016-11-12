/**
 * Copyright (c) 2016-present, Daniel Lo Nigro (Daniel15)
 * All rights reserved.
 *
 * This source code is licensed under the MIT-style license found in the
 * LICENSE file in the root directory of this source tree.
 */
package org.jenkinsci.plugins.githubissues.exceptions;

/**
 * Exception thrown when we fail to load the GitHub repository details for a project.
 */
public class GitHubRepositoryException extends Exception {
    public GitHubRepositoryException(String message) {
        super(message);
    }
    public GitHubRepositoryException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
