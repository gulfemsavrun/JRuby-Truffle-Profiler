// TODO: needs copyright message from Gulfem

package org.jruby.truffle.nodes.profiler;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrument.Instrument;
import com.oracle.truffle.api.instrument.impl.DefaultEventReceiver;
import com.oracle.truffle.api.nodes.Node;

public final class TypeDistributionProfilerInstrument extends DefaultEventReceiver {

    private final Node initialNode;
    @CompilationFinal private Node onlyNode;
    @CompilationFinal private long onlyCounter;
    private final Map<Class<? extends Node>, Counter> types;

    public TypeDistributionProfilerInstrument(Node initialNode) {
        this.initialNode = initialNode;
        this.onlyNode = initialNode;
        this.onlyCounter = 0;
        this.types = new HashMap<>();
    }

    @Override
    public void returnVoid(Node astNode, VirtualFrame frame) {
        if (onlyNode == initialNode) {
            onlyNode = astNode;
            onlyCounter++;
        } else if (onlyNode.getClass().equals(astNode.getClass())) {
            onlyCounter++;
        } else {
            addNewNodeOrIncrement(astNode);
        }
    }

    @Override
    public void returnValue(Node astNode, VirtualFrame frame, Object result) {
        returnVoid(astNode, frame);
    }

    @Override
    public void returnExceptional(Node astNode, VirtualFrame frame, Exception e) {
        returnVoid(astNode, frame);
    }

    @CompilerDirectives.TruffleBoundary
    private void addNewNodeOrIncrement(Node astNode) {
        if (!types.containsKey(onlyNode.getClass())) {
            this.types.put(onlyNode.getClass(), new Counter(onlyCounter));
        }

        if (types.containsKey(astNode.getClass())) {
            types.get(astNode.getClass()).increment();
        } else {
            types.put(astNode.getClass(), new Counter());
        }
    }

    public Node getInitialNode() {
        return initialNode;
    }

    public Node getOnlyNode() {
        return onlyNode;
    }

    public long getOnlyCounter() {
        return onlyCounter;
    }

    public Map<Class<? extends Node>, Counter> getTypes() {
        return types;
    }
}
