import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by changyeonclarapark on 8/3/16.
 */
public class Trie {
    private HashMap<Character, TrieNode> startingLetters;

    public Trie() {
        startingLetters = new HashMap<>();
    }


    /** Recursively find the TrieNode that matches the given prefix String. */
    private TrieNode findPrefixNode(TrieNode node, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return node;
        }
        char character = prefix.charAt(0);
        for (Character c : node.nextChars.keySet()) {
            if (c.equals(character)) {
                findPrefixNode(node.nextChars.get(c), prefix.substring(1));
            }
        }
        return node;
    }

    /** Find the location names that match the given prefix and return a list of those words. */
    public List<String> findMatches(String prefix) {
        LinkedList<String> matches = new LinkedList<>();
        if (!startingLetters.containsKey(prefix.charAt(0))) {
            return matches;
        } else {
            TrieNode node = startingLetters.get(prefix.charAt(0));
            TrieNode from = findPrefixNode(node, prefix.substring(1));
            from.autocomplete(prefix, matches);
        }
        return matches;
    }

    /**
     * Adds string to the trie
     * @param name the string to be added
     */
    public void add(String name) {
        if (name.length() == 0) return;
        if (name.length() == 1) {
            if (startingLetters.containsKey(name.charAt(0))) {
                return;
            } else {
                startingLetters.put(name.charAt(0), null);
            }
        } else {
            if (startingLetters.containsKey(name.charAt(0))) {
                startingLetters.get(name.charAt(0)).add(name.substring(1));
            } else {
                startingLetters.put(name.charAt(0), new TrieNode());
            }
        }

    }


    public class TrieNode {
        private HashMap<Character, TrieNode> nextChars;
        private String name;
        private Character character;

        public TrieNode() {
            nextChars = new HashMap<>();
            character = null;
            name = null;
        }

        public TrieNode(Character character) { //not sure if we'll need this constructor
            this.character = character;
            nextChars = new HashMap<>();
            name = null;
        }

        public TrieNode(String name) {
            this.name = name; //where name is the full name of the location
            // - only leaf nodes have names -- similar to
                                //a definition in the Dictionary class
            nextChars = new HashMap<>();
        }

        /**
         * Helper function for add
         * @param tempName
         */
        public void add(String tempName) {
            if (tempName.length() == 1) {
                if (nextChars.containsKey(tempName.charAt(0))) {
                    return;
                } else {
                    nextChars.put(tempName.charAt(0), null);
                    return;
                }
            } else {
                if (nextChars.containsKey(tempName.charAt(0)) && nextChars
                        .get(tempName.charAt(0)) != null) {
                    nextChars.get(tempName.charAt(0)).add(tempName.substring(1));
                } else {
                    nextChars.put(tempName.charAt(0), new TrieNode());
                    nextChars.get(tempName.charAt(0)).add(tempName.substring(1));
                }
            }
        }

        /** Helper method. Check each character in nextChars of the TrieNode and collect
         * all possible endings of the prefix in a List of matches. Add each character to
         * the prefix each recursive call and add the whole word to the list of matches
         * once the end of a word is reached. */
        public void autocomplete(String prefix, List<String> matches) {
            for (Character c : nextChars.keySet()) {
                if (nextChars.get(c).nextChars == null) { //if it's the end of the word
                    matches.add(prefix);
                    return;
                } else {
                    autocomplete(prefix + c, matches);
                }
            } //what about case where a phrase is both a word and a prefix for a longer word?
        }
    }

}
