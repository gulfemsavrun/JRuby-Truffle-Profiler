// TODO: needs copyright message from Gulfem

package org.jruby.truffle.nodes.profiler;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrument.Instrument;
import com.oracle.truffle.api.instrument.impl.DefaultEventReceiver;
import com.oracle.truffle.api.nodes.Node;

public final class TimeProfilerInstrument extends DefaultEventReceiver {

    private final Node node;
    private long counter;
    private long startTime;
    private long totalElapsedTime;

    public TimeProfilerInstrument(Node node) {
        this.node = node;
        this.counter = 0;
        this.startTime = 0;
        this.totalElapsedTime = 0;
    }

    @Override
    public void enter(Node astNode, VirtualFrame frame) {
        this.counter++;
        this.startTime = System.nanoTime();
    }

    @Override
    public void returnVoid(Node astNode, VirtualFrame frame) {
        long endTime = System.nanoTime();
        long elapsedTime = endTime - startTime;
        this.totalElapsedTime = this.totalElapsedTime + elapsedTime;
    }

    @Override
    public void returnValue(Node astNode, VirtualFrame frame, Object result) {
        returnVoid(astNode, frame);
    }

    @Override
    public void returnExceptional(Node astNode, VirtualFrame frame, Exception e) {
        returnVoid(astNode, frame);
    }

    public Node getNode() {
        return node;
    }

    public long getCounter() {
        return counter;
    }

    public long getTime() {
        return totalElapsedTime;
    }

}
