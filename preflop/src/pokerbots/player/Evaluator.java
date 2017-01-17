package pokerbots.player;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Evaluator class contains methods to evaluate a hand
 */
public class Evaluator {

    public Card[] hand = new Card[2];

    public Evaluator(Card c1, Card c2) {
        this.hand[0] = c1;
        this.hand[1] = c2;
    }

    public Card[] sortCards(Card[] cards) {
        Arrays.sort(cards);
        return cards;
    }
}
