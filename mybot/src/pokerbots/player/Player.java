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

    private static final double MAXIMUM = 135191738;
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
        this.info.boardCards = new CardSet();
        for (int i = 0; i < numCards; i++) {
            this.info.boardCards.add(Card.valueOf(words[index++]));
        }
        this.info.allCards = new CardSet(this.info.boardCards);
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
    private void preflop(double rating) {

        //TODO:subject to change
        int amount = (int)(this.info.bb*rating/2);

        //good hand, use raise strategy
        if (rating >= 6 && this.info.isLegal("RAISE")) {
            int[] range = this.info.raiseRange();

            //Pair of jacks or better, always raise
            if (rating >= 12) {
                int strongAmount = amount*2;
                if (strongAmount>=range[0] && strongAmount<=range[1]) {
                    outStream.println("RAISE:"+strongAmount);
                } else if (strongAmount>range[1]){
                    System.out.println("ERROR: should not raise above (strong)");
                    outStream.println("RAISE:"+range[1]);
                } else {
                    //enormous raise from opp, reraise be careful, only raise on kings or aces
                    outStream.println("RAISE:"+range[0]);
                }
            }

            //Above average (QTu), consider raising
            else {
                if (amount>=range[0] && amount<=range[1]) {
                    outStream.println("RAISE:"+amount);
                } else if (amount > range[1]) {
                    System.out.println("ERROR: should not raise above (average)");
                    outStream.println("RAISE:"+range[1]);
                }
                //consider calling
                else if (this.info.potSize < amount) {
                    outStream.println("CALL");
                } else {
                    checkFold();
                }
            }
        }

        //call blind, call raises only on ok hand
        else if (this.info.isLegal("CALL")) {
            //good hand, call, this case must be they all in
            if (rating >= 14) {
                outStream.println("CALL");
            }
            //call blind
            else if (this.info.potSize < this.info.bb * 2) {
                outStream.println("CALL");
            } else if (rating >= 8 && this.info.getCallAmount() < amount) {
                outStream.println("CALL");
            } else {
                checkFold();
            }
        }

        //you are big blind, not good enough to raise on
        else {
            checkFold();
        }
    }

    /**
     * post flop strategy
     *
     * Hand Ratings
     * <0.12 - high card
     * .12-.24 - pair
     * .24-.37 - 2 pair
     * .37-.49 - 3 of a kind
     * .49-.62 - straight
     * .62-.74 - flush
     * .74-.86 - full house
     * .86-.9 - 4 of a kind
     * >.9 - straight flush
     */
    private void postflop() {
        Hand hand = Hand.eval(this.info.allCards);
        Hand board = Hand.eval(this.info.boardCards);

        double rating = (double)hand.getValue()/MAXIMUM;
        int amount = (int)(rating*100)/2 * this.info.bb;

        int[] range = this.info.raiseRange();

        //our pocket cards contribute to the rating i.e. opp doesnt have same rating
        if ((double)(hand.getValue() - board.getValue()) / hand.getValue() > 0.01) {

            //pair only bet/call/check/fold
            if (rating > .1 && rating < .24) {
                if (this.info.isLegal("BET")) {
                    outStream.println("BET:" + amount/2);
                } else if (this.info.isLegal("CALL") && this.info.getCallAmount() < amount) {
                    outStream.println("CALL");
                } else {
                    checkFold();
                }
            }
            //better than 2 pair, allow to reraise
            else if (rating > 0.24) {
                if (this.info.isLegal("BET")) {
                    outStream.println("BET: " + amount);
                } else if (this.info.isLegal("RAISE")) {
                    if (amount>=range[0] && amount<=range[1]) {
                        outStream.println("RAISE:" + amount);
                    } else if (amount>range[1]) {
                        outStream.println("RAISE:" + range[1]);
                    }
                    //must raise more than amount (reraise)
                    else if(rating > 0.35) {
                        outStream.println("RAISE:" + range[0]);
                    } else if (this.info.getCallAmount() < amount){
                        outStream.println("CALL");
                    } else {
                        checkFold();
                    }
                } else if (this.info.isLegal("CALL")) {
                    if (this.info.getCallAmount() < amount) {
                        outStream.println("CALL");
                    } else {
                        checkFold();
                    }
                } else {
                    checkFold();
                }
            } else {
                checkFold();
            }
        } else {
            //System.out.println("board strong: " + hand.toString() + this.info.pocket.toString());
            checkFold();
        }
    }

    private void turn() {}

    private void river() {}

    private void discard() {
        Hand hand = Hand.eval(this.info.allCards);
        Hand board = Hand.eval(this.info.boardCards);

        if (this.info.isLegal("DISCARD")) {
            if ((double)(hand.getValue() - board.getValue()) / hand.getValue() < 0.01 && this.info.pocketRating < 6) {
                System.out.println(this.info.pocket.getSecond().toString());
                outStream.println("DISCARD:" + this.info.pocket.getSecond().toString());
            }
        }
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

                    //Pre Flop Strategy
                    if (this.info.boardCards.size() == 0) {
                        preflop(rating);
                    }
                    //Post Flop
                    else if (this.info.boardCards.size() == 3) {
                        discard();
                        postflop();
                    }
                    //turn
                    else if (this.info.boardCards.size() == 4) {
                        //discard();
                        postflop();
                    }
                    //river
                    else if (this.info.boardCards.size() == 5) {
                        postflop();
                    }

                    else {
                        System.out.println("Should not reach: " + this.info.boardCards.toString());
                        checkFold();
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