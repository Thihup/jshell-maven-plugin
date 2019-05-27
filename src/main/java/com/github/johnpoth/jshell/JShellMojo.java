/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.johnpoth.jshell;

import javax.tools.Tool;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;


@Mojo( name = "run", defaultPhase = LifecyclePhase.INSTALL, requiresDependencyResolution = ResolutionScope.TEST, requiresDependencyCollection = ResolutionScope.TEST )
public class JShellMojo extends AbstractMojo
{

    @Parameter(defaultValue = "${project.runtimeClasspathElements}", property = "rcp", required = true)
    private List<String> runtimeClasspathElements;

    @Parameter(defaultValue = "${project.testClasspathElements}", property = "trcp", required = true)
    private List<String> testClasspathElements;

    @Parameter(defaultValue = "false", property = "testClasspath")
    private boolean testClasspath;

    @Parameter(defaultValue = "true", property = "jshell.useProjectClasspath")
    private boolean useProjectClasspath;

    @Parameter(property = "jshell.class-path")
    private String classpath;

    @Parameter(property = "jshell.module-path")
    private String modulepath;

    @Parameter(property = "jshell.add-modules")
    private String addModules;

    @Parameter(property = "jshell.add-exports")
    private String addExports;

    @Parameter(property = "jshell.scripts")
    private List<String> scripts = new ArrayList<>();

    // additional options that may be added in future Java releases.
    @Parameter(property = "jshell.options")
    private List<String> options = new ArrayList<>();

    public void execute() throws MojoExecutionException {
        String cp;
        if (testClasspath) {
            cp = testClasspathElements.stream().reduce(testClasspathElements.get(0), (a, b) -> a + File.pathSeparator + b);
        } else {
            cp = runtimeClasspathElements.stream().reduce(runtimeClasspathElements.get(0), (a, b) -> a + File.pathSeparator  + b);
        }
        getLog().debug("Using classpath:" + cp);
        Optional<Module> module = ModuleLayer.boot().findModule("jdk.jshell");
        ClassLoader classLoader = module.get().getClassLoader();
        // Until https://issues.apache.org/jira/browse/MNG-6371 is resolved
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(classLoader);
            ServiceLoader<Tool> sl = ServiceLoader.load(javax.tools.Tool.class);
            Tool jshell = sl.stream()
                    .filter(a -> a.get().name().equals("jshell"))
                    .findAny()
                    .orElseThrow(() -> new RuntimeException("No JShell service providers found!"))
                    .get();
            String[] args = addArguments(cp);
            jshell.run(System.in, System.out, System.err, args);
        } finally {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        }
    }

    private String[] addArguments(String cp) {
        int size = getArgumentsSize();
        String[] args = new String [size + options.size() + scripts.size()];
        int i = 0;
        if (useProjectClasspath) {
            args[i++] = "--class-path";
            args[i++] = cp;
        } else if (classpath != null ){
            args[i++] = "--class-path";
            args[i++] = classpath;
        }
        if (modulepath != null){
            args[i++] = "--module-path";
            args[i++] = modulepath;
        }
        if (addModules!= null){
            args[i++] = "--add-modules";
            args[i++] = modulepath;
        }
        if (addExports!= null){
            args[i++] = "--add-exports";
            args[i++] = modulepath;
        }
        for (String option : this.options) {
            args[i++] = option;
        }
        for (String script : scripts) {
            args[i++] = script;
        }
        return args;
    }

    private int getArgumentsSize() {
        int size = 0;
        if (useProjectClasspath || classpath != null) {
            size += 2;
        }
        if (modulepath != null) {
            size += 2;
        }
        if (addModules != null) {
            size += 2;
        }
        if (addExports != null) {
            size += 2;
        }
        return size;
    }
}
