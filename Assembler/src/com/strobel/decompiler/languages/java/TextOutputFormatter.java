/*
 * TextOutputFormatter.java
 *
 * Copyright (c) 2013 Mike Strobel
 *
 * This source code is subject to terms and conditions of the Apache License, Version 2.0.
 * A copy of the license can be found in the License.html file at the root of this distribution.
 * By using this source code in any fashion, you are agreeing to be bound by the terms of the
 * Apache License, Version 2.0.
 *
 * You must not remove this notice, or any other, from this software.
 */

package com.strobel.decompiler.languages.java;

import com.strobel.assembler.metadata.MemberReference;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.ParameterDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.core.VerifyArgument;
import com.strobel.decompiler.ITextOutput;
import com.strobel.decompiler.ast.Variable;
import com.strobel.decompiler.languages.TextLocation;
import com.strobel.decompiler.languages.java.ast.*;
import sun.reflect.generics.tree.TypeArgument;

import java.util.Stack;

public class TextOutputFormatter implements IOutputFormatter {
    private final ITextOutput output;
    private final Stack<AstNode> nodeStack = new Stack<>();
    private int braceLevelWithinType = -1;
    private boolean inDocumentationComment = false;
    private boolean firstUsingDeclaration;
    private boolean lastUsingDeclaration;

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final Stack<TextLocation> startLocations = new Stack<>();

    public TextOutputFormatter(final ITextOutput output) {
        this.output = VerifyArgument.notNull(output, "output");
    }

    @Override
    public void startNode(final AstNode node) {
        if (nodeStack.isEmpty()) {
            if (isImportDeclaration(node)) {
                firstUsingDeclaration = !isImportDeclaration(node.getPreviousSibling());
                lastUsingDeclaration = !isImportDeclaration(node.getNextSibling());
            }
            else {
                firstUsingDeclaration = false;
                lastUsingDeclaration = false;
            }
        }

        nodeStack.push(node);
        startLocations.push(new TextLocation(output.getRow(), output.getColumn()));

        if (node instanceof EntityDeclaration &&
            node.getUserData(Keys.MEMBER_REFERENCE) != null &&
            node.getChildByRole(Roles.IDENTIFIER).isNull()) {

            output.writeDefinition("", node.getUserData(Keys.MEMBER_REFERENCE), false);
        }
    }

    @Override
    public void endNode(final AstNode node) {
        if (nodeStack.pop() != node) {
            throw new IllegalStateException();
        }

        startLocations.pop();
    }

    @Override
    public void writeLabel(final String label) {
        output.writeLabel(label);
    }

    @Override
    public void writeIdentifier(final String identifier) {
        Object reference = getCurrentTypeReference();

        if (reference != null) {
            output.writeReference(identifier, reference);
            return;
        }

        reference = getCurrentMemberReference();

        if (reference != null) {
            output.writeReference(identifier, reference);
            return;
        }

        Object definition = getCurrentDefinition();

        if (definition != null) {
            output.writeDefinition(identifier, definition, false);
            return;
        }

        definition = getCurrentLocalDefinition();

        if (definition != null) {
            output.writeDefinition(identifier, definition);
            return;
        }

        reference = getCurrentLocalReference();

        if (reference != null) {
            output.writeReference(identifier, reference, true);
            return;
        }

        if (firstUsingDeclaration) {
            output.markFoldStart("", true);
            firstUsingDeclaration = false;
        }

        output.write(identifier);
    }

    @Override
    public void writeKeyword(final String keyword) {
        output.writeKeyword(keyword);
    }

    @Override
    public void writeOperator(final String token) {
        output.writeOperator(token);
    }

    @Override
    public void writeToken(final String token) {
/*
        final MemberReference member = getCurrentMemberReference();
        final AstNode node = nodeStack.peek();

        if (member != null && node.getChildByRole(Roles.IDENTIFIER).isNull()) {
            output.writeReference(token, member);
        }
        else {
            output.write(token);
        }
*/
        output.write(token);
    }

    @Override
    public void writeLiteral(final String value) {
        output.writeLiteral(value);
    }

    @Override
    public void writeTextLiteral(final String value) {
        output.writeTextLiteral(value);
    }

    @Override
    public void space() {
        output.write(' ');
    }

    @Override
    public void openBrace(final BraceStyle style) {
        if (braceLevelWithinType >= 0 || nodeStack.peek() instanceof TypeDeclaration) {
            braceLevelWithinType++;
        }

        int blockDepth = 0;

        for (final AstNode node : nodeStack) {
            if (node instanceof BlockStatement) {
                ++blockDepth;
            }
        }

        if (blockDepth <= 1) {
            output.markFoldStart("", braceLevelWithinType == 1);
        }

        switch (style) {
            case EndOfLine:
                space();
                break;
            case EndOfLineWithoutSpace:
                break;
            case NextLine:
                output.writeLine();
                break;
            case NextLineShifted:
                output.writeLine();
                output.indent();
                break;
            case NextLineShifted2:
                output.writeLine();
                output.indent();
                output.indent();
                break;
            case BannerStyle:
                break;
        }

        output.writeLine("{");
        output.indent();
    }

    @Override
    public void closeBrace(final BraceStyle style) {
        output.unindent();
        output.write('}');

        switch (style) {
            case NextLineShifted:
                output.unindent();
                break;
            case NextLineShifted2:
                output.unindent();
                output.unindent();
                break;
        }

        int blockDepth = 0;

        for (final AstNode node : nodeStack) {
            if (node instanceof BlockStatement) {
                ++blockDepth;
            }
        }

        if (blockDepth <= 1) {
            output.markFoldEnd();
        }

        if (braceLevelWithinType >= 0) {
            braceLevelWithinType--;
        }
    }

    @Override
    public void indent() {
        output.indent();
    }

    @Override
    public void unindent() {
        output.unindent();
    }

    @Override
    public void newLine() {
        if (lastUsingDeclaration) {
            output.markFoldEnd();
            lastUsingDeclaration = false;
        }
        output.writeLine();
    }

    @Override
    public void writeComment(final CommentType commentType, final String content) {
        switch (commentType) {
            case SingleLine: {
                output.write("//");
                output.writeLine(content);
                break;
            }

            case MultiLine: {
                output.write("/*");
                output.write(content);
                output.write("*/");
                break;
            }

            case Documentation: {
                final boolean isLastLine = !(nodeStack.peek().getNextSibling() instanceof Comment);

                if (!inDocumentationComment && !isLastLine) {
                    inDocumentationComment = true;
                    output.markFoldStart("///" + content, true);
                }

                output.write("///");
                output.write(content);

                if (inDocumentationComment && isLastLine) {
                    inDocumentationComment = false;
                    output.markFoldEnd();
                }

                output.writeLine();
                break;
            }

            default: {
                output.write(content);
                break;
            }
        }
    }

    private Object getCurrentDefinition() {
        if (nodeStack == null || nodeStack.isEmpty()) {
            return null;
        }

        Object definition;

        final AstNode node = nodeStack.peek();

        if (isDefinition(node)) {
            definition = node.getUserData(Keys.TYPE_DEFINITION);

            if (definition != null) {
                return definition;
            }

            definition = node.getUserData(Keys.METHOD_DEFINITION);

            if (definition != null) {
                return definition;
            }

            definition = node.getUserData(Keys.FIELD_DEFINITION);

            if (definition != null) {
                return definition;
            }
        }

        if (node.getRole() == Roles.IDENTIFIER) {
            final AstNode parent = node.getParent();

            if (parent == null) {
                return null;
            }

            definition = parent.getUserData(Keys.TYPE_DEFINITION);

            if (definition != null) {
                return definition;
            }

            definition = parent.getUserData(Keys.METHOD_DEFINITION);

            if (definition != null) {
                return definition;
            }

            definition = parent.getUserData(Keys.FIELD_DEFINITION);

            if (definition != null) {
                return definition;
            }
        }

        return null;
    }

    private MemberReference getCurrentTypeReference() {
        final AstNode node = nodeStack.peek();
        final TypeReference typeReference = node.getUserData(Keys.TYPE_REFERENCE);

        if (typeReference != null) {
            return typeReference;
        }

        if (node instanceof Identifier) {
            final AstNode parent = node.getParent();

            if (parent instanceof TypeArgument ||
                parent instanceof TypeParameterDeclaration) {

                return parent.getUserData(Keys.TYPE_REFERENCE);
            }
        }

        return null;
    }

    private MemberReference getCurrentMemberReference() {
        final AstNode node = nodeStack.peek();

        MemberReference member = node.getUserData(Keys.MEMBER_REFERENCE);

        if (member == null &&
            node.getRole() == Roles.TARGET_EXPRESSION &&
            (node.getParent() instanceof InvocationExpression || node.getParent() instanceof ObjectCreationExpression)) {

            member = node.getParent().getUserData(Keys.MEMBER_REFERENCE);
        }

        return member;
    }

    private Object getCurrentLocalReference() {
        final AstNode node = nodeStack.peek();

        Variable variable = node.getUserData(Keys.VARIABLE);

        if (variable == null && node instanceof Identifier && node.getParent() != null) {
            variable = node.getParent().getUserData(Keys.VARIABLE);
        }

        if (variable != null) {
            if (variable.isParameter()) {
                return variable.getOriginalParameter();
            }
            return variable.getOriginalVariable();
        }

        return null;
    }

    private Object getCurrentLocalDefinition() {
        AstNode node = nodeStack.peek();

        if (node instanceof Identifier && node.getParent() != null) {
            node = node.getParent();
        }

        final ParameterDefinition parameter = node.getUserData(Keys.PARAMETER_DEFINITION);

        if (parameter != null) {
            return parameter;
        }

        if (node instanceof VariableInitializer || node instanceof CatchClause/* || node instanceof ForEachStatement*/) {
            Variable variable = node.getUserData(Keys.VARIABLE);

            if (variable == null && node.getParent() instanceof VariableDeclarationStatement) {
                variable = node.getParent().getUserData(Keys.VARIABLE);
            }

            if (variable != null) {
                if (variable.getOriginalParameter() != null) {
                    return variable.getOriginalParameter();
                }

                return variable.getOriginalVariable();
            }
        }

        if (node instanceof LabelStatement) {
            final LabelStatement label = (LabelStatement) node;

            for (int i = nodeStack.size() - 1; i >= 0; i--) {
                final AstNode n = nodeStack.get(i);
                final MemberReference methodReference = n.getUserData(Keys.MEMBER_REFERENCE);

                if (methodReference instanceof MethodReference) {
                    return methodReference + label.getLabel();
                }
            }
        }

        return null;
    }

    private static boolean isDefinition(final AstNode node) {
        return node instanceof EntityDeclaration;
    }

    private boolean isImportDeclaration(final AstNode node) {
        return node instanceof ImportDeclaration;
    }
}
