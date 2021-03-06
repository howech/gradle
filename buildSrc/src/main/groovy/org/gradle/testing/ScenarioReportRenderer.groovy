package org.gradle.testing

import groovy.util.slurpersupport.NodeChildren
import groovy.xml.MarkupBuilder

class ScenarioReportRenderer {
    void render(Writer writer, String projectName, List<Object> finishedBuilds, Map<String, List<File>> testResultFilesForBuild) {
        def markup = new MarkupBuilder(writer)

        markup.html {
            head {
                title "Performance test scenarios report"
                meta("http-equiv": "Content-Type", content: "text/html; charset=utf-8")
                link(rel: "stylesheet", type: "text/css", href: "scenario-report-style.css")
            }
            def buildsSuccessOrNot = finishedBuilds.sort(false) { build -> getScenarioId(build)?:'' }.groupBy { build -> build.@status.toString() == 'SUCCESS' }
            def successfullBuilds = buildsSuccessOrNot.get(true)
            def otherBuilds = buildsSuccessOrNot.get(false)
            if (otherBuilds) {
                h3 'Failed scenarios'
                renderResultTable(markup, projectName, otherBuilds, testResultFilesForBuild, true)
            }
            if (successfullBuilds) {
                h3 'Successful scenarios'
                renderResultTable(markup, projectName, successfullBuilds, testResultFilesForBuild)
            }
        }
    }

    private renderResultTable(markup, projectName, builds, Map<String, List<File>> testResultFilesForBuild, failed = false) {
        def closure = {
            table {
                thead {
                    tr {
                        th("Scenario")
                        th("")
                        if (failed) {
                            th("")
                        }
                        th("")
                    }
                }
                builds.each { build ->
                    def rowClass = build.@status.toString().toLowerCase()
                    tr(class: rowClass) {
                        td(this.getScenarioId(build))
                        td {
                            a(href: build.@webUrl, "Go to this Build")
                        }
                        if (failed) {
                            td {
                                a(href: "https://builds.gradle.org/repository/download/${build.@buildTypeId}/${build.@id}:id/reports/${projectName}/fullPerformanceTest/index.html", "Test Summary")
                            }
                        }
                        td {
                            a(href: "report/tests/${this.getScenarioId(build).replaceAll(" ", "-")}.html", "Performance Report")
                        }
                    }
                    if (failed) {
                        def testResultXmlFiles = testResultFilesForBuild.get(build.@id.text())
                        if (testResultXmlFiles) {
                            for (File testResultXmlFile : testResultXmlFiles) {
                                def testresult = new XmlSlurper().parse(testResultXmlFile)
                                testresult.testcase.failure.each { failure ->
                                    tr(class: rowClass) {
                                        td(colspan: 4) {
                                            span(class: 'code') {
                                                pre(failure.text())
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        closure.delegate = markup
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
    }


    private String getScenarioId(Object build) {
        NodeChildren properties = build.properties.children()
        properties.find { it.@name == 'scenario' }.@value.text()
    }

    void writeCss(File directory) {
        def cssFile = new File(directory, "scenario-report-style.css")
        def input = getClass().getResourceAsStream("scenario-report-style.css")
        input.withStream {
            cssFile.withOutputStream { out ->
                out << input
            }
        }
    }
}
