import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by Paul on 8/4/16.
 */
public class DictionaryBasedTrie {
    /*
     * Since a trie node can have so many children, the children it has are
     * stored in a map.
     */
    private HashMap<Character, TrieNode> myStartingLetters;

    public DictionaryBasedTrie() {
        myStartingLetters = new HashMap<>();
    }

    /**
     * Associates the input word with the input definition in the dictionary.
     */
    public void addWord(String word) {
        String searchVersion = GraphDB.cleanString(word);
        if (searchVersion.length() == 0) return;
        if (searchVersion.length() == 1) {
            if (myStartingLetters.containsKey(searchVersion.charAt(0))) {
                myStartingLetters.get(searchVersion.charAt(0)).myWord = word;
            } else {
                myStartingLetters.put(searchVersion.charAt(0), new TrieNode(word));
            }
        } else {
            if (myStartingLetters.containsKey(searchVersion.charAt(0))) {
                myStartingLetters.get(searchVersion.charAt(0))
                        .addWord(searchVersion.substring(1), word);
            } else {
                myStartingLetters.put(searchVersion.charAt(0), new TrieNode());
                myStartingLetters.get(searchVersion.charAt(0)).
                        addWord(searchVersion.substring(1), word);
            }
        }
    }

    /**
     * Return the definition associated with this word in the Dictionary. Return
     * null if there is no definition for the word.
     */
    public List<String> lookupWords(String prefix) {
        if (prefix.length() == 1) {
            if (myStartingLetters.containsKey(prefix.charAt(0))) {
                LinkedList<String> returnList = new LinkedList<>();
                returnList.add(myStartingLetters.get(prefix.charAt(0)).myWord);
                return returnList;
            } else return null;
        } else {
            if (myStartingLetters.containsKey(prefix.charAt(0))) {
                return myStartingLetters.get(prefix.charAt(0)).lookup(prefix.substring(1));
            } else return null;
        }
    }

    private class TrieNode {
        private HashMap<Character, TrieNode> myNextLetters;

        // Leave this null if this TrieNode is not the end of a complete word.
        private String myWord;

        private TrieNode() {
            myNextLetters = new HashMap<>();
        }

        private TrieNode(String word) {
            myWord = word;
            myNextLetters = new HashMap<>();
        }


        private List<String> lookup(String prefix) {
            LinkedList<String> returnList = new LinkedList<>();
            if (prefix.length() == 0) {
                this.autocomplete(returnList);
                return returnList;
            } else if (myNextLetters.containsKey(prefix.charAt(0))) {
                return myNextLetters.get(prefix.charAt(0)).lookup(prefix.substring(1));
            } else return returnList;
        }

        private void autocomplete(LinkedList<String> returnList) {
            if (this.myWord != null) {
                returnList.add(this.myWord);
            }
            Iterator<Character> myNextLetterIter = this.myNextLetters.keySet().iterator();
            while (myNextLetterIter.hasNext()) {
                Character c = myNextLetterIter.next();
                myNextLetters.get(c).autocomplete(returnList);
            }

        }

        /**
         * recursively adds definition
         * @param characters is the word to be added
         */
        private void addWord(String characters, String word) {
            if (characters.length() == 1) {
                if (myNextLetters.containsKey(characters.charAt(0))) {
                    myNextLetters.get(characters.charAt(0)).myWord = word;
                } else {
                    myNextLetters.put(characters.charAt(0), new TrieNode(word));
                }
            } else {
                if (myNextLetters.containsKey(characters.charAt(0))) {
                    myNextLetters.get(characters.charAt(0)).addWord(characters.substring(1), word);
                } else {
                    myNextLetters.put(characters.charAt(0), new TrieNode());
                    myNextLetters.get(characters.charAt(0)).addWord(characters.substring(1), word);
                }
            }
        }
    }
}
