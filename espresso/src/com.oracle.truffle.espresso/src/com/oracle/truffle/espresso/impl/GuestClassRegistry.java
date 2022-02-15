/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.perf.DebugCounter;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;

/**
 * A {@link GuestClassRegistry} maps class names to resolved {@link Klass} instances. Each class
 * loader is associated with a {@link GuestClassRegistry} and vice versa.
 *
 * This class is analogous to the ClassLoaderData C++ class in HotSpot.
 */
public final class GuestClassRegistry extends ClassRegistry {

    static final DebugCounter loadKlassCount = DebugCounter.create("Guest loadKlassCount");
    static final DebugCounter loadKlassCacheHits = DebugCounter.create("Guest loadKlassCacheHits");

    @Override
    protected void loadKlassCountInc() {
        loadKlassCount.inc();
    }

    @Override
    protected void loadKlassCacheHitsInc() {
        loadKlassCacheHits.inc();
    }

    /**
     * The class loader associated with this registry.
     */
    private final StaticObject classLoader;

    // The virtual method can be cached because the receiver (classLoader) is constant.
    private final Method loadClass;
    private final Method addClass;

    public GuestClassRegistry(EspressoContext context, @JavaType(ClassLoader.class) StaticObject classLoader) {
        super(context.getLanguage().getNewLoaderId());
        assert StaticObject.notNull(classLoader) : "cannot be the BCL";
        this.classLoader = classLoader;
        this.loadClass = classLoader.getKlass().lookupMethod(Name.loadClass, Signature.Class_String);
        this.addClass = classLoader.getKlass().lookupMethod(Name.addClass, Signature._void_Class);
        if (context.getJavaVersion().modulesEnabled()) {
            StaticObject unnamedModule = context.getMeta().java_lang_ClassLoader_unnamedModule.getObject(classLoader);
            initUnnamedModule(unnamedModule);
            context.getMeta().HIDDEN_MODULE_ENTRY.setHiddenObject(unnamedModule, getUnnamedModule());
        }
    }

    @Override
    public ParserKlass loadParserKlass(ClassLoadingEnv env, Symbol<Type> type, ClassRegistry.ClassDefinitionInfo info) {
        ObjectKlass klass = performLoadKlass(env, type, info);
        return klass.getLinkedKlass().getParserKlass();
    }

    @Override
    public LinkedKlass loadLinkedKlass(ClassLoadingEnv env, Symbol<Type> type, ClassRegistry.ClassDefinitionInfo info) {
        ObjectKlass klass = performLoadKlass(env, type, info);
        return klass.getLinkedKlass();
    }

    private ObjectKlass performLoadKlass(ClassLoadingEnv env, Symbol<Type> type, ClassRegistry.ClassDefinitionInfo info) {
        if (!(env instanceof ClassLoadingEnv.InContext)) {
            throw EspressoError.shouldNotReachHere("This registry cannot load classes in no-context environment");
        }
        ClassLoadingEnv.InContext contextEnv = (ClassLoadingEnv.InContext) env;
        Klass klass = loadKlassImpl(contextEnv, type, info);
        return (ObjectKlass) klass;
    }

    @Override
    public Klass loadKlassImpl(ClassLoadingEnv.InContext env, Symbol<Type> type, ClassRegistry.ClassDefinitionInfo info) {
        assert StaticObject.notNull(classLoader);
        StaticObject guestClass = (StaticObject) loadClass.invokeDirect(classLoader, env.getMeta().toGuestString(Types.binaryName(type)));
        Klass klass = guestClass.getMirrorKlass();
        env.getRegistries().recordConstraint(type, klass, getClassLoader());
        ClassRegistries.RegistryEntry entry = new ClassRegistries.RegistryEntry(klass);
        ClassRegistries.RegistryEntry previous = classes.putIfAbsent(type, entry);
        assert previous == null || previous.klass() == klass;
        return klass;
    }

    @Override
    public @JavaType(ClassLoader.class) StaticObject getClassLoader() {
        return classLoader;
    }

    @SuppressWarnings("sync-override")
    @Override
    public ObjectKlass defineKlass(ClassLoadingEnv.InContext env, Symbol<Type> typeOrNull, final byte[] bytes, ClassRegistry.ClassDefinitionInfo info) throws EspressoClassLoadingException {
        ObjectKlass klass = super.defineKlass(env, typeOrNull, bytes, info);
        // Register class in guest CL. Mimics HotSpot behavior.
        if (info.addedToRegistry()) {
            addClass.invokeDirect(classLoader, klass.mirror());
        }
        return klass;
    }
}
