package com.redhat.jhalliday.impl.javaparser.printer;

/*
 * Copyright (C) 2007-2010 Júlio Vilmar Gesser.
 * Copyright (C) 2011, 2013-2020 The JavaParser Team.
 *
 * This file is part of JavaParser.
 *
 * JavaParser can be used either under the terms of
 * a) the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * b) the terms of the Apache License
 *
 * You should have received a copy of both licenses in LICENCE.LGPL and
 * LICENCE.APACHE. Please refer to those files for details.
 *
 * JavaParser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 */

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.redhat.jhalliday.InfoExtractor;
import com.redhat.jhalliday.impl.HighInfoExtractor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Pretty printer for AST nodes.
 */
public class HighLevelBodyExtractor implements BiFunction<MethodDeclaration, Map<String, String>, String> {

    private final PrettyPrinterConfigurationMod configuration;
    private final VoidVisitor<Void> visitor;

    public HighLevelBodyExtractor() {

        this.configuration = new PrettyPrinterConfigurationMod();
        this.configuration.setExtraWhiteSpace(true);
        this.configuration.setPrintComments(false);
        this.configuration.setEndOfLineCharacter(" ");
        this.configuration.setColumnAlignFirstMethodChain(false);
        this.configuration.setIndentCaseInSwitch(false);
        this.configuration.setIndentSize(0);

        visitor = configuration.getVisitorFactory().apply(this.configuration);
    }

    @Override
    public String apply(MethodDeclaration declaration, Map<String, String> placeholders) {
        return ProcessMethod(declaration);
    }

    private String ProcessMethod(MethodDeclaration methodDeclaration) {
        // it is not needed to check if there is a body because
        // all previous operations (in the Driver.java) make sure that a body is present
        methodDeclaration.getBody().get().accept(visitor, null);

        String tempBody = visitor.toString();

        tempBody = tempBody.replaceAll("\\s+", " ");
        tempBody = tempBody.replaceAll("\\s+\\}\\s+$", "");
        tempBody = tempBody.replaceAll("\\s+\\{\\s+", "");
        return tempBody;
    }
}