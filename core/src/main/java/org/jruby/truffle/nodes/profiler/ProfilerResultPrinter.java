package org.jruby.truffle.nodes.profiler;

import java.io.PrintStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jruby.truffle.nodes.RubyRootNode;
import org.jruby.truffle.nodes.call.RubyCallNode;
import org.jruby.util.cli.Options;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;

public class ProfilerResultPrinter {

    private PrintStream out = System.err;

    private final ProfilerProber profilerProber;

    public ProfilerResultPrinter(ProfilerProber profilerProber) {
        this.profilerProber = profilerProber;
    }
    
    public void printCallProfilerResults() {
        long totalCount = 0;
        List<TimeProfilerInstrument> callInstruments = profilerProber.getCallInstruments();

        if (callInstruments.size() > 0) {
            printBanner("Call Profiling Results", 72);
            /**
             * 50 is the length of the text by default padding left padding is added, so space is
             * added to the beginning of the string, minus sign adds padding to the right
             */

            out.format("%-45s", "Function Name");
            out.format("%-20s", "Number of Calls");
            out.format("%-20s", "Total Time");
            out.format("%-9s", "Line");
            out.format("%-11s", "Column");
            out.format("%-11s", "Length");
            out.println();
            out.println("===============                              ===============     ===============     ====     ======     ======");

            for (TimeProfilerInstrument instrument : callInstruments) {
                if (instrument.getCounter() > 0) {
                	Node node = instrument.getNode();
                    out.format("%-45s", ((RubyRootNode)node.getRootNode()).getSharedMethodInfo());
                    out.format("%15s", instrument.getCounter());
                    out.format("%20s", (instrument.getTime() / 1000000000));
                    out.format("%9s", node.getSourceSection().getStartLine());
                    out.format("%11s", node.getSourceSection().getStartColumn());
                    out.format("%11s", node.getSourceSection().getCharLength());
                    out.println();

                    totalCount = totalCount + instrument.getCounter();
                }
            }
            
			out.println("Total number of executed instruments: " + totalCount);
        }
    }

    public void printControlFlowProfilerResults() {
        printWhileProfilerResults();
        printIteratorLoopProfilerResults();
        printIfProfilerResults();
        printBreakNextProfilerResults();
    }
    
    private void printWhileProfilerResults() {        
        long totalCount = 0;
        List<ProfilerInstrument> whileInstruments = getInstruments(profilerProber.getWhileInstruments());   
        
        if (whileInstruments.size() > 0) {
            printCaption("While Loop Profiling Results");
            
            for (ProfilerInstrument instrument : whileInstruments) {
                if (instrument.getCounter() > 0) {
                    Node node = instrument.getNode();
                    Node whileNode = node.getParent().getParent();
                    String nodeName = getShortName(whileNode);
                    printProfilerResult(nodeName, whileNode, instrument.getCounter());
                    totalCount = totalCount + instrument.getCounter();
                }
            }

            out.println("Total number of executed instruments: " + totalCount);
        }
    }

    private void printIteratorLoopProfilerResults() {
        long totalCount = 0;
        List<ProfilerInstrument> iteratorLoopInstruments = getInstruments(profilerProber.getIteratorLoopInstruments()); 

        if (iteratorLoopInstruments.size() > 0) {
            printCaption("Iterator Loop Profiling Results");

            for (ProfilerInstrument instrument : iteratorLoopInstruments) {
                if (instrument.getCounter() > 0) {
                    Node node = instrument.getNode();
                    out.format("%-50s", ((RubyRootNode)node.getRootNode()).getSharedMethodInfo());
                    out.format("%15s", instrument.getCounter());
                    totalCount = totalCount + instrument.getCounter();
                    out.format("%9s", node.getSourceSection().getStartLine());
                    out.format("%11s", node.getSourceSection().getStartColumn());
                    out.format("%11s", node.getSourceSection().getCharLength());
                    out.format("%5s", "");
                    out.format("%-70s", node.getRootNode());
                    out.println();
                }
            }

            out.println("Total number of executed instruments: " + totalCount);
        }
    }
    
    public void printIfProfilerResults() {
        long totalCount = 0;
        Map<ProfilerInstrument, List<ProfilerInstrument>> ifInstruments;
        if (Options.TRUFFLE_PROFILE_SORT.load()) {
            ifInstruments = sortIfProfilerResults(profilerProber.getIfInstruments());
        } else {
            ifInstruments = profilerProber.getIfInstruments(); 
        }

        if (ifInstruments.size() > 0) {
            printBanner("If Node Profiling Results", 120);
            out.format("%-20s", "If Counter");
            out.format("%15s", "Then Counter");
            out.format("%20s", "Else Counter");
            out.format("%9s", "Line");
            out.format("%11s", "Column");
            out.format("%11s", "Length");
            out.format("%5s", "");
            out.format("%-70s", "In Method");
            out.println();
            out.println("===========            ============        ============     ====     ======     ======     ========================================================");

            Iterator<Map.Entry<ProfilerInstrument, List<ProfilerInstrument>>> it = ifInstruments.entrySet().iterator();
            while (it.hasNext()) {
                Entry<ProfilerInstrument, List<ProfilerInstrument>> entry = it.next();
                ProfilerInstrument ifInstrument = entry.getKey();
                if (ifInstrument.getCounter() > 0) {
                    List<ProfilerInstrument> instruments = entry.getValue();
                    ProfilerInstrument thenInstrument = instruments.get(0);
                    out.format("%11s", ifInstrument.getCounter());
                    out.format("%24s", thenInstrument.getCounter());

                    totalCount = totalCount + ifInstrument.getCounter();
                    totalCount = totalCount + thenInstrument.getCounter();

                    if (instruments.size() == 1) {
                        out.format("%20s", "-");
                    } else if (instruments.size() == 2) {
                        ProfilerInstrument elseInstrument = instruments.get(1);
                        out.format("%20s", elseInstrument.getCounter());
                        totalCount = totalCount + elseInstrument.getCounter();
                    }

                    Node ifNode = ifInstrument.getNode();
                    out.format("%9s", ifNode.getSourceSection().getStartLine());
                    out.format("%11s", ifNode.getSourceSection().getStartColumn());
                    out.format("%11s", ifNode.getSourceSection().getCharLength());
                    out.format("%5s", "");
                    out.format("%-70s", ifNode.getRootNode());
                    out.println();
                }
            }

            out.println("Total number of executed instruments: " + totalCount);
        }
    }

    public void printBreakNextProfilerResults() {
	    long totalCount = 0;
	    List<ProfilerInstrument> breakNextInstruments = getInstruments(profilerProber.getBreakNextInstruments());   

	    if (breakNextInstruments.size() > 0) {
	        printCaption("Break/Next Profiling Results");

	        for (ProfilerInstrument instrument : breakNextInstruments) {
	            if (instrument.getCounter() > 0) {
	                Node node = instrument.getNode();
	                String nodeName = getShortName(node);
	                printProfilerResult(nodeName, node, instrument.getCounter());
	                totalCount = totalCount + instrument.getCounter();
	            }
	        }
	
	        out.println("Total number of executed instruments: " + totalCount);
	    }
    }
    
    public void printVariableAccessProfilerResults() {
        if (Options.TRUFFLE_PROFILE_TYPE_DISTRIBUTION.load()) {
            long totalCount = 0;
            List<TypeDistributionProfilerInstrument> variableAccessInstruments = profilerProber.getVariableAccessTypeDistributionInstruments();           
            
            printBanner("Variable Access Profiling Type Distribution Results", 140);

            if (variableAccessInstruments.size() > 0) {
                out.format("%-50s", "Node");
                out.format("%-20s", "Counter");
                out.format("%-9s", "Line");
                out.format("%-11s", "Column");
                out.format("%-11s", "Length");
                out.format("%-70s", "In Method");
                out.println();
                out.println("=============                                     ===============     ====     ======     ======     =======================================================");
  
                for (TypeDistributionProfilerInstrument profilerInstrument : variableAccessInstruments) {
                    Map<Class<? extends Node>, Counter> types = profilerInstrument.getTypes();
                    if (types.isEmpty()) {
                        Node initialNode = profilerInstrument.getInitialNode();
                        Node onlyNode = profilerInstrument.getOnlyNode();
                        long counter = profilerInstrument.getOnlyCounter();
                        Class<? extends Node> nodeClass = onlyNode.getClass();
                        totalCount = totalCount + counter;
                        out.format("%-50s", nodeClass.getSimpleName());
                        out.format("%15s", counter);
                        out.format("%9s", initialNode.getSourceSection().getStartLine());
                        out.format("%11s", initialNode.getSourceSection().getStartColumn());
                        out.format("%11s", initialNode.getSourceSection().getCharLength());
                        out.format("%5s", "");
                        out.format("%-70s", initialNode.getRootNode());
                        out.println();
                    } else {
                        Iterator<Map.Entry<Class<? extends Node>, Counter>> it = types.entrySet().iterator();
                        out.println();

                        while (it.hasNext()) {
                            Entry<Class<? extends Node>, Counter> entry = it.next();
                            Node initialNode = profilerInstrument.getInitialNode();
                            Class<? extends Node> nodeClass = entry.getKey();
                            long counter = entry.getValue().getCounter();
                            totalCount = totalCount + counter;
                            out.format("%-50s", nodeClass.getSimpleName());
                            out.format("%15s", counter);
                            out.format("%9s", initialNode.getSourceSection().getStartLine());
                            out.format("%11s", initialNode.getSourceSection().getStartColumn());
                            out.format("%11s", initialNode.getSourceSection().getCharLength());
                            out.format("%5s", "");
                            out.format("%-70s", initialNode.getRootNode());
                            out.println();
                        }

                        out.println();
                    }
                }
            }      
        } else { 
            long totalCount = 0;
            List<ProfilerInstrument> variableAccessInstruments = getInstruments(profilerProber.getVariableAccessInstruments()); 
            
            if (variableAccessInstruments.size() > 0) {
                printCaption("Variable Access Profiling Results");
                
                for (ProfilerInstrument instrument : variableAccessInstruments) {
                    if (instrument.getCounter() > 0) {
                        Node node = instrument.getNode();
                        String nodeName = getShortName(node);                   
                        printProfilerResult(nodeName, node, instrument.getCounter());
                        totalCount = totalCount + instrument.getCounter();
                    }
                }
                
                out.println("Total number of executed instruments: " + totalCount);
            }    
        }
    }

    public void printOperationProfilerResults() {
        long totalCount = 0;
        List<ProfilerInstrument> operationInstruments = getInstruments(profilerProber.getOperationInstruments()); 
        
        if (operationInstruments.size() > 0) {
            printCaption("Operation Profiling Results");
            
            for (ProfilerInstrument instrument : operationInstruments) {
                if (instrument.getCounter() > 0) {
                    Node node = instrument.getNode();
                    String nodeName =  ((RubyCallNode) node).getName();                    
                    printProfilerResult(nodeName, node, instrument.getCounter());
                    totalCount = totalCount + instrument.getCounter();
                }
            }
            
            out.println("Total number of executed instruments: " + totalCount);
        }
    }
    
    public void printCollectionOperationProfilerResults() {
        long totalCount = 0;
        List<ProfilerInstrument> collectionOperationInstruments = getInstruments(profilerProber.getCollectionOperationInstruments());

        if (collectionOperationInstruments.size() > 0) {
            printCaption("Collection Operator Profiling Results");

            for (ProfilerInstrument instrument : collectionOperationInstruments) {
                if (instrument.getCounter() > 0) {
                    Node node = instrument.getNode();
                    String nodeName =  ((RubyCallNode) node).getName();
                    printProfilerResult(nodeName, node, instrument.getCounter());
                    totalCount = totalCount + instrument.getCounter();
                }
            }

            out.println("Total number of executed instruments: " + totalCount);
        }
    }
    
    private void printCaption(String caption) {
        printBanner(caption, 110);
        out.format("%-25s", "Node");
        out.format("%-20s", "Counter");
        out.format("%-9s", "Line");
        out.format("%-11s", "Column");
        out.format("%-11s", "Length");
        out.format("%-70s", "In Method");
        out.println();
        out.println("=============            ===============     ====     ======     ======     =======================================================");
    }

    private void printProfilerResult(String nodeName, Node node, long counter) {
        out.format("%-25s", nodeName);
        out.format("%15s", counter);
        out.format("%9s", node.getSourceSection().getStartLine());
        out.format("%11s", node.getSourceSection().getStartColumn());
        out.format("%11s", node.getSourceSection().getCharLength());
        out.format("%5s", "");
        out.format("%-70s", node.getRootNode());
        out.println();
    }
    
    private static String getShortName(Node node) {
        NodeInfo nodeInfo = node.getClass().getAnnotation(NodeInfo.class);

        if (nodeInfo == null) {
            nodeInfo = node.getClass().getSuperclass().getAnnotation(NodeInfo.class);
        } else if (nodeInfo.shortName().equals("")) {
            nodeInfo = node.getClass().getSuperclass().getAnnotation(NodeInfo.class);
        }

        if (nodeInfo != null) {
            return nodeInfo.shortName();
        } else {
            throw new RuntimeException("Short name is missing in " + node);
        }
    }
	
    private static List<ProfilerInstrument> getInstruments(List<ProfilerInstrument> instruments) {
        if (Options.TRUFFLE_PROFILE_SORT.load()) {
            List<ProfilerInstrument> sortedInstruments = sortProfilerResult(instruments);
            return sortedInstruments;
        }

        return instruments;
    }
	
    private static List<ProfilerInstrument> sortProfilerResult(List<ProfilerInstrument> list) {
        Collections.sort(list, new Comparator<ProfilerInstrument>() {
            @Override
            public int compare(final ProfilerInstrument profiler1, final ProfilerInstrument profiler2) {
                return Long.compare(profiler2.getCounter(), profiler1.getCounter());
            }
        });

        return list;
    }
    
    private static Map<ProfilerInstrument, List<ProfilerInstrument>> sortIfProfilerResults(Map<ProfilerInstrument, List<ProfilerInstrument>> map) {
        List<Map.Entry<ProfilerInstrument, List<ProfilerInstrument>>> list = new LinkedList<>(map.entrySet());

        Collections.sort(list, new Comparator<Map.Entry<ProfilerInstrument, List<ProfilerInstrument>>>() {

            public int compare(Map.Entry<ProfilerInstrument, List<ProfilerInstrument>> if1, Map.Entry<ProfilerInstrument, List<ProfilerInstrument>> if2) {
                return Long.compare(if2.getKey().getCounter(), if1.getKey().getCounter());
            }
        });

        Map<ProfilerInstrument, List<ProfilerInstrument>> result = new LinkedHashMap<>();
        for (Map.Entry<ProfilerInstrument, List<ProfilerInstrument>> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;

    }
    
    private static void printBanner(String caption, int size) {
        // CheckStyle: stop system..print check
        for (int i = 0; i < size / 2; i++) {
            System.err.print("=");
        }

        System.err.print(" " + caption + " ");

        for (int i = 0; i < size / 2; i++) {
            System.err.print("=");
        }

        System.err.println();
        // CheckStyle: resume system..print check
    }
}
