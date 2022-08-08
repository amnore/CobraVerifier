package graph;

import static history.Event.EventType.READ;
import static history.Event.EventType.WRITE;

import java.util.*;

import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraph;
import com.google.common.graph.ValueGraphBuilder;

import org.apache.commons.lang3.tuple.Pair;

import history.History;
import history.Transaction;
import lombok.Getter;

@SuppressWarnings("UnstableApiUsage")
@Getter
public class KnownGraph<KeyType, ValueType> {
    private final MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Edge<KeyType>>> readFrom = ValueGraphBuilder
            .directed().build();
    private final MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Edge<KeyType>>> knownGraphA = ValueGraphBuilder
            .directed().build();
    private final MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Edge<KeyType>>> knownGraphB = ValueGraphBuilder
            .directed().build();

    /**
     * Build a graph from a history
     *
     * The built graph contains SO and WR edges
     */
    public KnownGraph(History<KeyType, ValueType> history) {
        history.getTransactions().forEach(txn -> {
            knownGraphA.addNode(txn);
            knownGraphB.addNode(txn);
            readFrom.addNode(txn);
        });

        // add SO edges
        history.getSessions().forEach(session -> {
            Transaction<KeyType, ValueType> prevTxn = null;
            for (var txn : session.getTransactions()) {
                if (prevTxn != null) {
                    addEdge(knownGraphA, prevTxn, txn,
                            new Edge<>(EdgeType.SO, List.of()));
                }
                prevTxn = txn;
            }
        });

        // add WR edges
        var writes = new HashMap<Pair<KeyType, ValueType>, Transaction<KeyType, ValueType>>();
        var events = history.getEvents();

        events.stream().filter(e -> e.getType() == WRITE).forEach(ev -> writes
                .put(Pair.of(ev.getKey(), ev.getValue()), ev.getTransaction()));

        events.stream().filter(e -> e.getType() == READ).forEach(ev -> {
            var writeTxn = writes.get(Pair.of(ev.getKey(), ev.getValue()));
            var txn = ev.getTransaction();

            if (writeTxn == txn) {
                return;
            }

            putEdge(writeTxn, txn, new Edge<>(EdgeType.WR, List.of(ev.getKey())));
        });
    }

    public void putEdge(Transaction<KeyType, ValueType> u,
            Transaction<KeyType, ValueType> v, Edge<KeyType> edge) {
        switch (edge.getType()) {
        case WR:
            addEdge(readFrom, u, v, edge);
            // fallthrough
        case WW:
        case SO:
            addEdge(knownGraphA, u, v, edge);
            break;
        case RW:
            addEdge(knownGraphB, u, v, edge);
            break;
        }
    }

    private void addEdge(
            MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Edge<KeyType>>> graph,
            Transaction<KeyType, ValueType> u,
            Transaction<KeyType, ValueType> v,
            Edge<KeyType> edge) {
        if (!graph.hasEdgeConnecting(u, v)) {
            graph.putEdgeValue(u, v, new ArrayList<>());
        }

        var list = (ArrayList<Edge<KeyType>>) graph.edgeValue(u, v).get();
        var idx = list.indexOf(edge);
        if (idx == -1) {
            list.add(new Edge<>(edge.getType(), new ArrayList<>(edge.getKeys())));
        } else {
            list.get(idx).getKeys().addAll(edge.getKeys());
        }
    }
}
