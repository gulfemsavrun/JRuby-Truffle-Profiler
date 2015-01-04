// TODO: needs copyright message from Gulfem

package org.jruby.truffle.nodes.profiler;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrument.Instrument;
import com.oracle.truffle.api.instrument.TruffleEventReceiver;
import com.oracle.truffle.api.instrument.impl.DefaultEventReceiver;
import com.oracle.truffle.api.nodes.Node;

/**
 * @author Gulfem
 */

public final class ProfilerInstrument extends DefaultEventReceiver implements TruffleEventReceiver {

    private final Node node;
    private long counter;

    public ProfilerInstrument(Node node) {
        this.node = node;
        this.counter = 0;
    }

    @Override
    public void enter(Node astNode, VirtualFrame frame) {
        this.counter++;
    }

    public Node getNode() {
        return node;
    }

    public long getCounter() {
        return counter;
    }

}