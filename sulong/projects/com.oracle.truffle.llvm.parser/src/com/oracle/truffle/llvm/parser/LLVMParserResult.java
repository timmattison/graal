/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.llvm.parser.model.functions.FunctionSymbol;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.runtime.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;

public final class LLVMParserResult {

    private final LLVMParserRuntime runtime;
    private final List<FunctionSymbol> definedFunctions;
    private final List<FunctionSymbol> externalFunctions;
    private final List<GlobalVariable> definedGlobals;
    private final List<GlobalVariable> externalGlobals;
    private List<ExternalLibrary> dependencies;
    private final DataLayout dataLayout;
    private final int symbolTableSize;

    LLVMParserResult(LLVMParserRuntime runtime,
                    List<FunctionSymbol> definedFunctions,
                    List<FunctionSymbol> externalFunctions,
                    List<GlobalVariable> definedGlobals,
                    List<GlobalVariable> externalGlobals,
                    DataLayout dataLayout) {
        this.runtime = runtime;
        this.definedFunctions = definedFunctions;
        this.externalFunctions = externalFunctions;
        this.definedGlobals = definedGlobals;
        this.externalGlobals = externalGlobals;
        this.dataLayout = dataLayout;
        this.symbolTableSize = definedFunctions.size() + externalFunctions.size() + definedGlobals.size() + externalGlobals.size();
    }

    public LLVMParserRuntime getRuntime() {
        return runtime;
    }

    public List<FunctionSymbol> getDefinedFunctions() {
        return definedFunctions;
    }

    public List<FunctionSymbol> getExternalFunctions() {
        return externalFunctions;
    }

    public List<GlobalVariable> getDefinedGlobals() {
        return definedGlobals;
    }

    public List<GlobalVariable> getExternalGlobals() {
        return externalGlobals;
    }

    public List<ExternalLibrary> getDependencies() {
        return dependencies;
    }

    public DataLayout getDataLayout() {
        return dataLayout;
    }

    @Override
    public String toString() {
        return "LLVMParserResult[" + runtime.getLibrary() + "]";
    }

    public int getSymbolTableSize() {
        return symbolTableSize;
    }

    public void setDependencies(ArrayList<ExternalLibrary> dependencies) {
        assert this.dependencies == null;
        this.dependencies = dependencies;
    }
}
