/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.util.tap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tap implementation that aggregates information on a per-thread basis. An
 * instance of this class maintains a collection of subordinate Taps, one
 * per thread. These are created on demand as new threads invoke
 * {@link #in()} or {@link #out()}.
 * <p/>
 * By default each subordinate Tap is a {@link TimeAndCount}, but the
 * two-argument constructor provides a way to override that default.
 */

abstract class PerThread extends Tap
{
    // Object interface

    @Override
    public String toString()
    {
        return "PerThread(" + name + ")";
    }

    // Tap interface

    @Override
    public void in()
    {
        threadTap().in();
    }

    @Override
    public void out()
    {
        threadTap().out();
    }

    @Override
    public long getDuration()
    {
        return threadTap().getDuration();
    }

    @Override
    public void reset()
    {
        for (Tap tap : threadMap.values()) {
            tap.reset();
        }
        enabled = true;
    }

    @Override
    public void disable()
    {
        for (Tap tap : threadMap.values()) {
            tap.disable();
        }
        enabled = false;
    }

    @Override
    public void appendReport(StringBuilder buffer)
    {
        Map<Thread, Tap> threadMap = new TreeMap<Thread, Tap>(THREAD_COMPARATOR);
        threadMap.putAll(this.threadMap);
        // TODO - fix this: thread names not necessarily unique. putAll could lose threads!
        assert this.threadMap.size() == threadMap.size();
        for (Map.Entry<Thread, Tap> entry : threadMap.entrySet()) {
            buffer.append(NEW_LINE);
            buffer.append("==");
            buffer.append(entry.getKey().getName());
            buffer.append("==");
            buffer.append(NEW_LINE);
            entry.getValue().appendReport(buffer);
        }
    }

    @Override
    public TapReport[] getReports()
    {
        Map<String, TapReport> cumulativeReports = new HashMap<String, TapReport>();
        for (Tap tap : threadMap.values()) {
            assert tap.getReports() != null; // because tap is not a Dispatch or Null.
            for (TapReport tapReport : tap.getReports()) {
                String tapName = tapReport.getName();
                TapReport cumulativeReport = cumulativeReports.get(tapName);
                if (cumulativeReport == null) {
                    cumulativeReport = new TapReport(tapName);
                    cumulativeReports.put(tapName, cumulativeReport);
                }
                cumulativeReport.inCount += tapReport.getInCount();
                cumulativeReport.outCount += tapReport.getOutCount();
                cumulativeReport.cumulativeTime += tapReport.getCumulativeTime();
            }
        }
        if (cumulativeReports.isEmpty()) {
            cumulativeReports.put(name, new TapReport(name));
        }
        TapReport[] reports = new TapReport[cumulativeReports.size()];
        cumulativeReports.values().toArray(reports);
        return reports;
    }
    
    // PerThread interface

    abstract Tap threadTap();

    /**
     * Create a PerThread instance the adds a new instance of the supplied
     * subclass of {@link com.akiban.util.tap.Tap} for each Thread. Note: the classes
     * {@link com.akiban.util.tap.PerThread} (including subclasses)
     * may not be added.
     *
     * @param name  Name of the Tap
     * @param tapClass Class from which new Tap instances are created.
     */
    public static PerThread createPerThread(String name, Class<? extends Tap> tapClass)
    {
        if (PerThread.class.isAssignableFrom(tapClass)) {
            throw new IllegalArgumentException();
        }
        return new Ordinary(name, tapClass);
    }
    
    public static PerThread createRecursivePerThread(String name, PerThread outermostRecursivePerThread)
    {
        return new Recursive(name, outermostRecursivePerThread);
    }

    // For use by subclasses

    protected PerThread(String name)
    {
        super(name);
    }

    // Class state

    private static final Comparator<Thread> THREAD_COMPARATOR = new Comparator<Thread>()
    {
        @Override
        public int compare(Thread x, Thread y)
        {
            return x.getName().compareTo(y.getName());
        }
    };
    
    // Object state

    final Map<Thread, Tap> threadMap = new ConcurrentHashMap<Thread, Tap>();
    boolean enabled = false;

    // Inner classes
    
    private static class Ordinary extends PerThread
    {
        // PerThread interface

        Tap threadTap()
        {
            return threadLocal.get();
        }

        // Ordinary interface
        
        Ordinary(String name, Class<? extends Tap> tapClass)
        {
            super(name);
            this.tapClass = tapClass;
        }

        private final Class<? extends Tap> tapClass;
        private final ThreadLocal<Tap> threadLocal = new ThreadLocal<Tap>()
        {
            @Override
            protected Tap initialValue()
            {
                Tap tap;
                try {
                    tap = tapClass.getConstructor(String.class).newInstance(name);
                    tap.reset();
                    if (!enabled) {
                        tap.disable();
                    }
                    threadMap.put(Thread.currentThread(), tap);
                } catch (Exception e) {
                    tap = new Null(name);
                    LOG.warn("Unable to create tap of class " + tapClass.getSimpleName(), e);
                }
                return tap;
            }
        };
    }
    
    private static class Recursive extends PerThread
    {
        // PerThread interface

        Tap threadTap()
        {
            return threadLocal.get();
        }
        
        // Recursive interface
        
        Recursive(String name, PerThread outermostRecursivePerThread)
        {
            super(name);
            this.outermostRecursivePerThread = outermostRecursivePerThread;
        }

        private final PerThread outermostRecursivePerThread;
        private final ThreadLocal<Tap> threadLocal = new ThreadLocal<Tap>()
        {
            @Override
            protected Tap initialValue()
            {
                Tap x = outermostRecursivePerThread.threadTap();
                RecursiveTap.Outermost outermost =
                    (RecursiveTap.Outermost) x;
                RecursiveTap tap = outermost.createSubsidiaryTimer(name);
                tap.reset();
                threadMap.put(Thread.currentThread(), tap);
                return tap;
            }
        };
    }
}