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

    private double check; //percentage check/call
    private double fold;  //percentage fold
    private int raise;    //amount raise
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
        if (this.info == null) System.out.println("shit ");
        if (words.length != 8) System.out.println("fuck " + words.length);
        this.info.handId = Integer.parseInt(words[1]);
        this.info.button = Boolean.parseBoolean(words[2]);
        this.info.card1 = new Card(words[3]);
        this.info.card2 = new Card(words[4]);
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
        Card[] cards = new Card[numCards];
        for (int i = 0; i < numCards; i++) {
            cards[i] = new Card(words[index++]);
        }
        this.info.boardCards = cards;

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

    public void run() {
        String input;
        try {
            // Block until engine sends us a packet; read it into input.
            while ((input = inStream.readLine()) != null) {

                // Here is where you should implement code to parse the packets
                // from the engine and act on it.
                System.out.println(input);

                String[] words = input.split(" ");
                if ("NEWGAME".compareToIgnoreCase(words[0]) == 0) {
                    newGameInfo(words);
                } else if ("NEWHAND".compareToIgnoreCase(words[0]) == 0) {
                    newHandInfo(words);
                } else if ("GETACTION".compareToIgnoreCase(words[0]) == 0) {
                    parseAction(words);
                    // When appropriate, reply to the engine with a legal
                    // action.
                    // The engine will ignore all spurious packets you send.
                    // The engine will also check/fold for you if you return an
                    // illegal action.
                    //outStream.println("CHECK");
                    if (Arrays.asList(this.info.legalActions).contains("CALL")) {
                        System.out.println("call");
                        outStream.println("CALL");
                    } else {
                        System.out.println("check");
                        outStream.println("CHECK");
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

