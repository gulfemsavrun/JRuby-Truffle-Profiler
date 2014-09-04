package org.jruby.truffle.nodes.profiler;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrument.Instrument;
import com.oracle.truffle.api.nodes.Node;

public final class TypeDistributionProfilerInstrument extends Instrument {

    private final Node initialNode;
    private final Map<Class<? extends Node>, Long> types;

    public TypeDistributionProfilerInstrument(Node initialNode) {
        this.initialNode = initialNode;
        this.types = new HashMap<>();
    }

    @Override
    public void enter(Node astNode, VirtualFrame frame) {
        if (types.containsKey(astNode.getClass())) {
            long counter = types.get(astNode.getClass());
            types.put(astNode.getClass(), counter + 1);
        } else {
            types.put(astNode.getClass(), 1L);
        }
    }
    
    public Node getInitialNode() {
        return initialNode;
    }

    public Map<Class<? extends Node>, Long> getTypes() {
        return types;
    }

    Comparator<Class<? extends Node>> order = new Comparator<Class<? extends Node>>() {
        public int compare(Class<? extends Node> class1, Class<? extends Node> class2) {
            if (class1.equals(class2)) {
                return 0;
            } else {
                return 1;
            }
        }
    };
}
