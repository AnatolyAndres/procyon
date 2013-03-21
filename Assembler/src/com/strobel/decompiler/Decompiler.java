/*
 * Decompiler.java
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

package com.strobel.decompiler;

import com.sampullara.cli.Args;
import com.strobel.assembler.metadata.Buffer;
import com.strobel.assembler.metadata.ClassFileReader;
import com.strobel.assembler.metadata.ClasspathTypeLoader;
import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.core.VerifyArgument;

import java.util.List;

/**
 * @author strobelm
 */
public final class Decompiler {
    public static void decompile(final String internalName, final ITextOutput output) {
        decompile(internalName, output, new DecompilerSettings());
    }

    public static void decompile(final String internalName, final ITextOutput output, final DecompilerSettings options) {
        VerifyArgument.notNull(internalName, "internalName");
        VerifyArgument.notNull(options, "options");

        final ClasspathTypeLoader loader = new ClasspathTypeLoader();
        final Buffer buffer = new Buffer();

        if (!loader.tryLoadType(internalName, buffer)) {
            output.writeLine("!!! ERROR: Failed to load class %s.", internalName);
            return;
        }

        final MetadataSystem metadataSystem = MetadataSystem.instance();

        final ClassFileReader reader = ClassFileReader.readClass(
            ClassFileReader.OPTION_PROCESS_CODE |
            ClassFileReader.OPTION_PROCESS_ANNOTATIONS,
            metadataSystem,
            buffer
        );

        final DecompilerVisitor typePrinter = new DecompilerVisitor(output, options);

        reader.accept(typePrinter);
    }

    public static void main(final String[] args) {
        final PlainTextOutput printer = new PlainTextOutput();
        final DecompilerSettings options = new DecompilerSettings();
        final List<String> typeNames = Args.parse(options, args);

        if (options.getPrintUsage()) {
            Args.usage(options);
            return;
        }

        if (typeNames.isEmpty()) {
            decompile("com/strobel/decompiler/Decompiler", printer, options);
        }
        else {
            for (final String typeName : typeNames) {
                decompile(typeName, printer, options);
            }
        }

        System.out.print(printer.toString());
    }
}