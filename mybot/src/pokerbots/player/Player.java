package pokerbots.player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Simple example pokerbot, written in Java.
 *
 * This is an example of a bare bones, pokerbot. It only sets up the socket
 * necessary to connect with the engine and then always returns the same action.
 * It is meant as an example of how a pokerbot should communicate with the
 * engine.
 *
 */
public class Player {

    private final PrintWriter outStream;
    private final BufferedReader inStream;

    private static final int MAXIMUM = 135191738;
    private Information info;

    public Player(PrintWriter output, BufferedReader input) {
        this.outStream = output;
        this.inStream = input;
//        this.check = 1/3.;
//        this.fold = 1/3.;
//        this.raise = 1;
    }

    /**
     * parses NewGame packet
     * NEWGAME yourName opp1Name stackSize bb numHands timeBank
     */
    public void newGameInfo(String[] words) {
        this.info = new Information(words);
    }

    /**
     * parses NewHand packet
     * NEWHAND handId button holeCard1 holeCard2 myBank otherBank timeBank
     */
    public void newHandInfo(String[] words) {
        if (this.info == null) System.out.println("input line empty");
        if (words.length != 8) System.out.println("input line not proper length 7: " + words.length);
        this.info.handId = Integer.parseInt(words[1]);
        this.info.button = Boolean.parseBoolean(words[2]);
        this.info.pocket = new Pocket(Card.valueOf(words[3]), Card.valueOf(words[4]));
        this.info.pocketRating = this.info.evalPocket();
        this.info.myBank = Integer.parseInt(words[5]);
        this.info.otherBank = Integer.parseInt(words[6]);
        this.info.timeBank = Double.parseDouble(words[7]);
    }

    /**
     * parses GetAction packet
     * GETACTION potSize numBoardCards [boardCards] numLastActions [lastActions] numLegalActions [legalActions] timebank
     */
    public void parseAction(String[] words) {
        int index = 1;
        this.info.potSize = Integer.parseInt(words[index++]);

        //should be able to this section smarter i.e. only add the new items from this round
        int numCards = Integer.parseInt(words[index++]);
        this.info.boardCards = new CardList();
        for (int i = 0; i < numCards; i++) {
            this.info.boardCards.add(Card.valueOf(words[index++]));
        }
        this.info.allCards = new CardSet();
        for (Card card: this.info.boardCards) {
            this.info.allCards.add(card);
        }
        this.info.allCards.add(this.info.pocket.getFirst());
        this.info.allCards.add(this.info.pocket.getSecond());

        int numActions = Integer.parseInt(words[index++]);
        String[] actions = new String[numActions];
        for (int i=0; i<numActions; i++) {
            actions[i] = words[index++];
        }
        this.info.lastActions = actions;

        int numLegal = Integer.parseInt(words[index++]);
        String[] legal = new String[numLegal];
        for (int i=0; i<numLegal; i++) {
            legal[i] = words[index++];
        }
        this.info.legalActions = legal;

        this.info.timeBank = Double.parseDouble(words[index]);

    }

    /**
     * will check if possible, otherwise fold
     */
    private void checkFold() {
        if (this.info.isLegal("CHECK")) {
            outStream.println("CHECK");
        } else {
            outStream.println("FOLD");
        }
    }

    /**
     * pre flop strategy, base on chen ratings
     */
    private void preflop(double rating, int stack) {
        if (rating >= 6 && this.info.isLegal("RAISE")) {
            int[] range = this.info.raiseRange();
            int amount = (int)(stack*rating/100);
            //Pair of jacks or better, always raise
            if (rating >= 12) {
                int strongAmount = Math.max(4*this.info.bb, amount*2);
                if (strongAmount>=range[0] && strongAmount<=range[1]) {
                    outStream.println("RAISE:"+strongAmount);
                } else if (amount>range[1]){
                    outStream.println("RAISE:"+range[1]);
                } else {
                    outStream.println("RAISE:"+range[0]);
                }
            }
            //Above average (QTu), consider raising
            else {
                if (amount>=range[0]) {
                    outStream.println("RAISE:"+amount);
                }
            }
        }

        if (rating >= 12 && this.info.isLegal("RAISE")) {
            int amount = Math.max(4*this.info.bb, (int)(stack*rating/50));
            int[] range = this.info.raiseRange();

        }
        //decent hand raise by a little
        else if (rating >= 6 && this.info.isLegal("RAISE")) {
            int amount = Math.max(this.info.bb, (int)(stack*rating/100));
            int[] range = this.info.raiseRange();
            if (this.info.bb <= (int)(stack*rating/100)) {
                outStream.println("RAISE:"+(int)(stack*rating/100));
            } else {
                checkFold();
            }
        }
        //call blind, call raises only on ok hand
        else if (this.info.isLegal("CALL")) {
            if (this.info.potSize < this.info.bb * 2) {
                outStream.println("CALL");
            } else if (rating >= 8 && this.info.potSize / 2 < (int) (stack * rating / 150)) {
                outStream.println("CALL");
            } else {
                checkFold();
            }
        }
        else {
            checkFold();
        }
    }

    /**
     * post flop strategy
     */
    private void postflop(double rating, int stack) {
        Hand hand = Hand.eval(this.info.allCards);
        System.out.println(hand.toString());
        int odd = (int)((double)(hand.getValue())/MAXIMUM * stack);
        if (this.info.isLegal("BET")) {
            System.out.println((double)(hand.getValue())/MAXIMUM * stack);
            if (odd > this.info.bb) {
                System.out.println("RAISE:" + odd);
                outStream.println("RAISE:" + odd);
            } else {
                checkFold();
            }
        }
        else if (this.info.isLegal("RAISE")) {
            System.out.println((double)(hand.getValue())/MAXIMUM * stack);
            if (odd > this.info.bb) {
                System.out.println("RAISE:" + odd);
                outStream.println("RAISE:" + odd);
            } else {
                checkFold();
            }
        } else if (this.info.isLegal("CALL")) {
            if (odd > this.info.potSize) {
                System.out.println("CALL");
                outStream.println("CALL");
            } else {
                checkFold();
            }
        } else {
            checkFold();
        }
    }

    private void turn() {}

    private void river() {}

    private void Discard() {
        //Discard Strategy
        /*CardSet set1 = new CardSet();
        CardSet set2 = new CardSet();
        for (Card card: this.info.boardCards) {
            set1.add(card);
            set2.add(card);
        }
        set1.add(this.info.pocket.getFirst());
        set2.add(this.info.pocket.getSecond());


        if (hand.getValue() == Hand.fastEval(set1)) {
            System.out.println("DISCARD:" + this.info.pocket.getSecond().toString());
            //outStream.println("DISCARD:" + this.info.pocket.getSecond().toString());
        } else if (hand.getValue() == Hand.fastEval(set2)) {
            System.out.println("DISCARD:" + this.info.pocket.getFirst().toString());
            //outStream.println("DISCARD:" + this.info.pocket.getFirst().toString());
        }*/
    }

    public void run() {
        String input;
        try {
            // Block until engine sends us a packet; read it into input.
            while ((input = inStream.readLine()) != null) {

                // Here is where you should implement code to parse the packets
                // from the engine and act on it.
                //System.out.println(input);

                String[] words = input.split(" ");
                if ("NEWGAME".compareToIgnoreCase(words[0]) == 0) {
                    newGameInfo(words);
                } else if ("NEWHAND".compareToIgnoreCase(words[0]) == 0) {
                    newHandInfo(words);
                } else if ("GETACTION".compareToIgnoreCase(words[0]) == 0) {
                    parseAction(words);
                    double rating = this.info.pocketRating;
                    int stack = this.info.stackSize + this.info.myBank;
                    //System.out.println(rating);
                    //System.out.println(rating + ", " + this.info.potSize/2 + ", " + stack);

                    //Pre Flop Strategy
                    if (this.info.boardCards.size() == 0) {
                        preflop(rating,stack);
                    }
                    //Post Flop
                    else if (this.info.boardCards.size() == 3) {
                        postflop(rating,stack);
                    }
                    //turn
                    else if (this.info.boardCards.size() == 4) {

                    }
                    //river
                    else if (this.info.boardCards.size() == 5) {

                    }
                } else if ("REQUESTKEYVALUES".compareToIgnoreCase(words[0]) == 0) {
                    // At the end, engine will allow bot to send key/value pairs to store.
                    // FINISH indicates no more to store.
                    outStream.println("FINISH");
                }
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }

        System.out.println("Gameover, engine disconnected");

        // Once the server disconnects from us, close our streams and sockets.
        try {
            outStream.close();
            inStream.close();
        } catch (IOException e) {
            System.out.println("Encounterd problem shutting down connections");
            e.printStackTrace();
        }
    }
}