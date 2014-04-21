package com.logscale.agent.util;

import java.util.Iterator;

public class TokenIterator implements Iterator<CharSequence> {
    private final CharSequence content;

    private int pos = 0;

    public TokenIterator(CharSequence content) {
        this.content = content;
        skip();
    }

    private void skip() {
        char ch;
        while (pos < content.length()) {
          ch = content.charAt(pos);
          if (isWordChar(ch)) {
              break;
          }
          pos++;
        }
    }

    private static boolean isWordChar(char ch) {
        return Character.isLetterOrDigit(ch) || Character.getType(ch) == Character.CONNECTOR_PUNCTUATION;
    }

    @Override
    public boolean hasNext() {
        return pos < content.length();
    }

    @Override
    public CharSequence next() {
        int startPos = pos++;
        char ch;
        while (pos < content.length()) {
            ch = content.charAt(pos);
            if (!isWordChar(ch)) {
                break;
            }
            pos++;
        }
        int endPos = pos;
        skip();
        return content.subSequence(startPos, endPos);
    }
}
