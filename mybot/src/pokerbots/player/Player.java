package pokerbots.player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

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

    private Information info;

    private CardList allpossible = new CardList();

    private double preasshole = 0;
    private double postasshole = 0;

    public Player(PrintWriter output, BufferedReader input) {
        this.outStream = output;
        this.inStream = input;
        for (Card.Suit suit: Card.Suit.values()) {
            for (Card.Rank rank: Card.Rank.values()) {
                this.allpossible.add(new Card(rank,suit));
            }
        }
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
        this.info.boardCards = new CardSet();
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

        if (this.info.timeBank <= 0) {
            System.out.println("ERROR: ran out of time: " + this.info.handId);
        }

        this.info.discardNew();
    }

    /**
     * gets the maximum possible score (with any two pocket cards) given the board cards
     */
    private double getMaximum() {
        double maximum = 0;
        for (Card card1: allpossible) {
            CardSet hand = new CardSet(this.info.boardCards);

            if (!this.info.boardCards.contains(card1)) {
                hand.add(card1);

                for (Card card2: allpossible) {
                    CardSet hand2 = new CardSet(hand);
                    if (!this.info.boardCards.contains(card2) && !card1.equals(card2)) {
                        hand2.add(card2);

                        double val = Hand.fastEval(hand2);
                        if (val > maximum) {
                            maximum = val;
                        }
                    }
                }
            }
        }
        return maximum;
    }

    /**
     * gets the fraction of possible opponent hands that beat ours
     */
    private double getBetter() {
        double temp = Hand.fastEval(this.info.allCards);
        int count = 0;
        int total = (52-this.info.allCards.size()) * (51-this.info.allCards.size());

        for (Card card1: allpossible) {
            CardSet hand = new CardSet(this.info.boardCards);

            if (!this.info.allCards.contains(card1)) {
                hand.add(card1);

                for (Card card2: allpossible) {
                    CardSet hand2 = new CardSet(hand);
                    if (!this.info.allCards.contains(card2) && !card1.equals(card2)) {
                        hand2.add(card2);

                        double val = Hand.fastEval(hand2);
                        if (val > temp) {
                            count++;
                        }
                    }
                }
            }
        }
        return (double)count/(double)total;
    }

    /**
     * gets the expected value of the outs on the next hand
     */
    private double getOut(CardSet allCards) {
        double count = 0;
        double total;

        if (this.info.boardCards.size() == 3) {
            total = (52-allCards.size())*(51-allCards.size());
            for (Card card1 : allpossible) {
                CardSet hand = new CardSet(allCards);

                if (!allCards.contains(card1)) {
                    hand.add(card1);

                    for (Card card2 : allpossible) {
                        CardSet hand2 = new CardSet(hand);
                        if (!allCards.contains(card2) && !card1.equals(card2)) {
                            hand2.add(card2);
                            count += Hand.fastEval(hand2);
                        }
                    }
                }
            }
            return count/total;
        } else if (this.info.boardCards.size() == 4) {
            total = (52-allCards.size());
            for (Card card: allpossible) {
                CardSet hand = new CardSet(allCards);

                if (!allCards.contains(card)) {
                    hand.add(card);
                    count += Hand.fastEval(hand);
                }
            }
            return count/total;
        } else {
            return Hand.fastEval(allCards);
        }
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
    private void preflop() {
        double rating = this.info.pocketRating;
        double offset = (preasshole/this.info.handId)*10;
        //upper bounded at 10 big binds
        int amount = (int)(this.info.bb*rating);

        //good hand, use raise strategy
        if (rating >= 5-offset && this.info.isLegal("RAISE")) {
            int[] range = this.info.raiseRange();

            //Pair of jacks or better, always raise, or call any
            if (rating >= 12 - offset) {
                int strongAmount = amount*4;
                if (strongAmount>=range[0] && strongAmount<=range[1]) {
                    outStream.println("RAISE:"+strongAmount);
                } else if (strongAmount>range[1]){
                    System.out.println("ERROR: should not raise above (strong)");
                    outStream.println("RAISE:"+range[1]);
                } else {
                    //enormous raise from opp, reraise be careful, only raise on Jacks, Queens, Kings or Aces
                    preasshole ++;
                    if (rating >= 12) {
                        outStream.println("RAISE:"+range[0]);
                    } else  {
                        outStream.println("CALL");
                    }

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
                else if (this.info.potSize <= this.info.bb*4 || (this.info.isLegal("CALL") && this.info.getCallAmount() < amount)) {
                    outStream.println("CALL");
                } else {
                    if (this.info.getCallAmount() > this.info.bb * 10) {
                        preasshole ++;
                    }
                    checkFold();
                }
            }
        }

        //call blind, call raises only on ok hand
        else if (this.info.isLegal("CALL")) {
            //good hand, call, this case must be they all in
            if (rating >= 12 - offset) {
                outStream.println("CALL");
            }
            //call blind
            else if (this.info.potSize <= this.info.bb * 2) {
                outStream.println("CALL");
            } else if (rating >= 8 && this.info.getCallAmount() < amount) {
                outStream.println("CALL");
            } else {
                if (this.info.getCallAmount() > this.info.bb * 20) {
                    preasshole ++;
                }
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
     * <0.12 (1,000,000) high card
     * .12-.24 (16,000,000 - 18,000,000) pair
     * .24-.37 - (33,000,000 - 35,000,000) 2 pair
     * .37-.49 - (50,000,000 - 52,000,000) 3 of a kind
     * .49-.62 - (67,000,000 - 69,000,000) straight
     * .62-.74 - (84,000,000 - 85,000,000) flush
     * .74-.86 - (100,000,000 - 102,000,000) full house
     * .86-.9 - (117,000,000 - 118,000,000) 4 of a kind
     * >.9 - (134,000,000 - 136,000,000) straight flush
     */
    private void postflop() {
        double maximum = getMaximum();
        double strength = 1-getBetter();
        double out = getOut(this.info.allCards)/maximum;
        double offset = postasshole/this.info.handId;

        Hand hand = Hand.eval(this.info.allCards);
        Hand board = Hand.eval(this.info.boardCards);

        //double rating = (double)hand.getValue()/maximum;

        //double temp = strength;
        strength += offset;
        strength = Math.min(1, (strength*0.8)+((out+0.3)*0.2) );

        //try discard
        if ((this.info.boardCards.size()==3 || this.info.boardCards.size()==4) && this.info.isLegal("DISCARD")) {
            discard(strength);
            return;
        }

        //System.out.println(this.info.pocket.toString() + ": " + hand.toString() + ", " + strength);
        //will raise ranging from 1 bb for 50/50 to 50 big blind for 100% win
        int amount = Math.max(0,(int)((strength-0.4) * 100))*this.info.bb;//(int)(rating/maximum*100)/2 * this.info.bb;

        int[] range = this.info.raiseRange();

        //our pocket cards contribute to the rating i.e. opp doesnt have same rating
        if (strength > 0.2 && (double)(hand.getValue() - board.getValue()) / hand.getValue() > 0.01) {

            //70% chance win, very strong
            if (strength > 0.6) {
                if (this.info.isLegal("BET")) {
                    int temp = Math.min(amount,range[1]);
                    if (temp > 0) {
                        outStream.println("BET:" + temp);
                    } else {
                        checkFold();
                    }
                } else if (this.info.isLegal("RAISE")) {
                    if (amount>=range[0] && amount<=range[1]) {
                        outStream.println("RAISE:" + amount);
                    } else if (amount>range[1]) {
                        outStream.println("RAISE:" + range[1]);
                    }
                    //must raise more than amount (reraise)
                    else if(strength > 0.85) {
                        outStream.println("RAISE:" + range[0]);
                    } else if (this.info.isLegal("CALL")) {
                        outStream.println("CALL");
                    } else {
                        postasshole++;
                        checkFold();
                    }
                } else if (this.info.isLegal("CALL")) {
                    if (this.info.getCallAmount() < amount) {
                        outStream.println("CALL");
                    } else {
                        if (this.info.getCallAmount() > this.info.bb*20) {
                            postasshole++;
                        }
                        checkFold();
                    }
                } else {
                    checkFold();
                }
            }
            else if (strength > 0.4) {
                if (this.info.isLegal("BET")) {
                    int temp = Math.min(amount,range[1]);
                    if (temp > 0) {
                        outStream.println("BET:" + temp);
                    } else {
                        checkFold();
                    }

                } else if (this.info.isLegal("CALL") && this.info.getCallAmount() < amount) {
                    outStream.println("CALL");
                } else {
                    if (this.info.isLegal("CALL") && this.info.getCallAmount() > this.info.bb*20) {
                        postasshole++;
                    }
                    checkFold();
                }
            }
            //shitty strength < 0.4
            else {
                checkFold();
            }
        } else {
            checkFold();
        }
    }


    /**
     * helper method used in discard to see which card to discard
     */
    private double whichDiscard(Card card) {
        CardSet allCards = this.info.boardCards;
        allCards.add(card);
        return getOut(allCards);
    }

    /**
     * discard strategy
     */
    private void discard(double strength) {
        Hand hand = Hand.eval(this.info.allCards);
        Hand board;

        if (this.info.boardCards.size() == 3 || this.info.boardCards.size() == 4) {
            board = Hand.eval(this.info.boardCards);
        } else {
            System.out.println("ERROR: should not discard with no board cards: " + this.info.boardCards.toString());
            checkFold();
            return;
        }

        if (this.info.isLegal("DISCARD")) {
            if ((double)(hand.getValue() - board.getValue()) / hand.getValue() < 0.01 && strength < 0.3) {
                //must decide to throw first or second
                if (whichDiscard(this.info.pocket.getFirst()) < whichDiscard(this.info.pocket.getSecond())) {
                    outStream.println("DISCARD:" + this.info.pocket.getFirst().toString());
                } else {
                    outStream.println("DISCARD:" + this.info.pocket.getSecond().toString());
                }
            } else {
                checkFold();
            }
        } else {
            System.out.println("ERROR: cannot discard, should not be called");
            checkFold();
        }
    }

    /**
     * main method to run
     */
    public void run() {
        String input;
        try {
            // Block until engine sends us a packet; read it into input.
            while ((input = inStream.readLine()) != null) {

                //System.out.println(input);

                String[] words = input.split(" ");
                if ("NEWGAME".compareToIgnoreCase(words[0]) == 0) {
                    newGameInfo(words);
                } else if ("NEWHAND".compareToIgnoreCase(words[0]) == 0) {
                    newHandInfo(words);
                } else if ("GETACTION".compareToIgnoreCase(words[0]) == 0) {
                    parseAction(words);
                    System.out.println(this.info.timeBank);
                    //Pre Flop Strategy
                    if (this.info.boardCards.size() == 0) {
                        preflop();
                    }
                    //Flop
                    else if (this.info.boardCards.size() == 3) {
                        postflop();
                    }
                    //turn
                    else if (this.info.boardCards.size() == 4) {
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