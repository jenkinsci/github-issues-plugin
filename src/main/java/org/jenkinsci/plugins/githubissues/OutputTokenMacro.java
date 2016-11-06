/**
 * Copyright (c) 2016-present, Daniel Lo Nigro (Daniel15)
 * All rights reserved.
 *
 * This source code is licensed under the MIT-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.jenkinsci.plugins.githubissues;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.tokenmacro.DataBoundTokenMacro;

import java.io.IOException;

/**
 * Token that returns build output.
 * See https://wiki.jenkins-ci.org/display/JENKINS/Token+Macro+Plugin
 */
@Extension
public class OutputTokenMacro extends DataBoundTokenMacro {
    /** Number of lines to display */
    @Parameter
    public int lines = 50;

    @Override
    public boolean acceptsMacroName(String macroName) {
        return macroName.equals("OUTPUT");
    }

    @Override
    public String evaluate(AbstractBuild<?, ?> context, TaskListener listener, String macroName) throws IOException {
        return StringUtils.join(context.getLog(lines), "\n");
    }
}
