/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.genscavenge;

import com.oracle.svm.core.jfr.JfrGCName;
import com.oracle.svm.core.jfr.JfrGCNames;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.util.VMError;

class JfrGCEventSupport {
    private static final int MAX_PHASE_LEVEL = 4;

    private final JfrGCName gcName;
    private int currentPhase;

    JfrGCEventSupport(JfrGCName gcName) {
        this.gcName = gcName;
    }

    public long getTicks() {
        return JfrTicks.elapsedTicks();
    }

    public long startGCPhasePause() {
        pushPhase();
        return JfrTicks.elapsedTicks();
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public void emitGarbageCollectionEvent(UnsignedWord gcEpoch, GCCause cause, long start) {
        long end = JfrTicks.elapsedTicks();
        long pauseTime = end - start;
        if (SubstrateJVM.isRecording() && SubstrateJVM.get().isEnabled(JfrEvent.GarbageCollection)) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            JfrNativeEventWriter.beginEventWrite(data, false);
            JfrNativeEventWriter.putLong(data, JfrEvent.GarbageCollection.getId());
            JfrNativeEventWriter.putLong(data, start);
            JfrNativeEventWriter.putLong(data, pauseTime);
            JfrNativeEventWriter.putLong(data, gcEpoch.rawValue());
            JfrNativeEventWriter.putLong(data, gcName.getId());
            JfrNativeEventWriter.putLong(data, cause.getId());
            JfrNativeEventWriter.putLong(data, pauseTime);  // sum of pause
            JfrNativeEventWriter.putLong(data, pauseTime);  // longest pause
            JfrNativeEventWriter.endEventWrite(data, false);
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public void emitGCPhasePauseEvent(UnsignedWord gcEpoch, String name, long startTicks) {
        int level = popPhase();
        JfrEvent event = getGCPhasePauseEvent(level);
        if (SubstrateJVM.isRecording() && SubstrateJVM.get().isEnabled(event)) {
            long end = JfrTicks.elapsedTicks();
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            JfrNativeEventWriter.beginEventWrite(data, false);
            JfrNativeEventWriter.putLong(data, event.getId());
            JfrNativeEventWriter.putLong(data, startTicks);
            JfrNativeEventWriter.putLong(data, end - startTicks);
            JfrNativeEventWriter.putEventThread(data);
            JfrNativeEventWriter.putLong(data, gcEpoch.rawValue());
            JfrNativeEventWriter.putString(data, name);
            JfrNativeEventWriter.endEventWrite(data, false);
        }
    }

    /**
     * GCPhasePause events are used to group GC phases into a hierarchy. They don't have any
     * predefined meaning as they are used in a GC-specific way. The most descriptive part is the
     * phase name that the GC emits as part of those JFR events.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static JfrEvent getGCPhasePauseEvent(int level) {
        switch (level) {
            case 0:
                return JfrEvent.GCPhasePauseEvent;
            case 1:
                return JfrEvent.GCPhasePauseLevel1Event;
            case 2:
                return JfrEvent.GCPhasePauseLevel2Event;
            case 3:
                return JfrEvent.GCPhasePauseLevel3Event;
            case 4:
                return JfrEvent.GCPhasePauseLevel4Event;
            default:
                throw VMError.shouldNotReachHere("GC phase pause level must be between 0 and 4.");
        }
    }

    private void pushPhase() {
        assert currentPhase < MAX_PHASE_LEVEL;
        currentPhase++;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private int popPhase() {
        assert currentPhase > 0;
        return --currentPhase;
    }
}

@AutomaticFeature
class JfrGCEventFeature implements Feature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.UseSerialGC.getValue();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (HasJfrSupport.get()) {
            JfrGCName name;
            if (SubstrateOptions.UseEpsilonGC.getValue()) {
                name = JfrGCNames.singleton().addGCName("epsilon");
            } else {
                assert SubstrateOptions.UseSerialGC.getValue();
                name = JfrGCNames.singleton().addGCName("serial");
            }

            ImageSingletons.add(JfrGCEventSupport.class, new JfrGCEventSupport(name));
        }
    }
}
