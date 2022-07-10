package tinkerbeast.ds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Trie<K extends CharSequence> {

    class Node {
        Map<Character, Node> next = new HashMap<>();
        int index = -1;
    };

    Node root_ = new Node();
    int count = 0;
    Map<Integer, K> keySet = new HashMap<>();

    public void put(K key) {
        Node cur = root_;
        for(int i = 0; i < key.length(); ++i) {
            char ch = key.charAt(i);
            Node next = cur.next.get(ch);
            if (null == next) {
                next = new Node();
                cur.next.put(ch, next);
            }
            cur = next;
        }
        if (cur.index == -1) {
            //System.out.format("DEBUG Trie.put i=%d k=%s %n", count, key);
            cur.index = count;
            keySet.put(count, key);
            ++count;
        }
    }

    public K[] remove(Object arg0) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Method is not yet implemented");
    }
 

    public void clear() {
        root_ = new Node();
    }

    public List<K> get(K arg0) {
        
        CharSequence key = (CharSequence)arg0;
        ArrayList<K> items = new ArrayList<>();
        
        Node cur = root_;
        for(int i = 0; i < key.length(); ++i)  {                        
            char ch = key.charAt(i);
            cur = cur.next.get(ch);
            if (null == cur) {                
                return items;
            }            
        }

        //System.out.format("DEBUG Trie.get k=%s cur=%s %n", key, cur);
        if (cur.index != -1) {
            items.add(keySet.get(cur.index));                
        }
        get_recurseive(cur, items);
                
        return items;
    }
    
    private void get_recurseive(Node cur, ArrayList<K> items) {
        for(HashMap.Entry<Character, Node> kv : cur.next.entrySet()) {
            int itemIdx = kv.getValue().index;
            if (itemIdx != -1) {
                K item = keySet.get(itemIdx);
                items.add(item);
            }
            get_recurseive(kv.getValue(), items);
        }
    }

    public boolean isEmpty() {
        return root_.index == -1 && root_.next.isEmpty();
    }

    public int size() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Method is not yet implemented");
    }
}