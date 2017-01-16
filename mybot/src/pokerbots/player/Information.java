package pokerbots.player;

/**
 * Information class to store all the information from packets
 */
public class Information {
    public String yourName;
    public String oppName;
    public int bb;
    public int numHands;
    public int handId;
    public boolean button;
    public Pocket pocket;
    public double pocketRating;
    public int stackSize;
    public int myBank;
    public int otherBank;
    public double timeBank;
    public int potSize;
    public CardSet allCards;
    public CardList boardCards;
    public String[] lastActions;
    public String[] legalActions;

    /**
     * initializes information from newgame packet
     * NEWGAME yourName opp1Name stackSize bb numHands timeBank
     */
    public Information(String[] words) {
        this.yourName = words[1];
        this.oppName = words[2];
        this.stackSize = Integer.parseInt(words[3]);
        this.myBank = 0;
        this.otherBank = 0;
        this.bb = Integer.parseInt(words[4]);
        this.numHands = Integer.parseInt(words[5]);
        this.timeBank = Double.parseDouble(words[6]);
    }

    /**
     * returns whether the move is legal, moves are
     * CHECK, CALL, RAISE, FOLD
     */
    public boolean isLegal(String move) {
        for (String str: this.legalActions) {
            if (str.contains(move)) {
                return true;
            }
        }
        return false;
    }

    /**
     * checks if amount is legal to be raised or bet
     */
    public int[] raiseRange() {
        int[] range = {0,0};
        if (!isLegal("RAISE") && !isLegal("BET")) {
            System.out.println("Raise/Bet is not possible");
            return range;
        } else {
            for (String str: this.legalActions) {
                if (str.contains("RAISE") || str.contains("BET")) {
                    String[] item = str.split(":");
                    range[0] = Integer.parseInt(item[1]);
                    range[1] = Integer.parseInt(item[2]);
                    return range;
                }
            }
            System.out.println("Raise/Bet not possible");
            return range;
        }
    }

    /**
     * evaluate hand by chen method
     *
     */
    public double evalPocket() {
        double rating = 1;
        Card c1 = this.pocket.getFirst();
        int highCard = c1.getRank().getValue();
        switch (highCard) {
            case 14:
                rating = 10;
                break;
            case 13:
                rating = 8;
                break;
            case 12:
                rating = 7;
                break;
            case 11:
                rating = 6;
                break;
            default:
                rating = (double)(highCard)/2.;
                break;
        }
        if (this.pocket.isPair()) {
            rating *= 2;
            if (rating < 5) rating = 5;
        }
        if (this.pocket.isSuited()) {
            rating += 1;
        }
        switch (this.pocket.getGap()) {
            case 0:
                break;
            case 1:
                rating+=.5;
                break;
            case 2:
                rating-=1;
                break;
            case 3:
                rating-=2;
                break;
            case 4:
                rating-=4;
                break;
            default:
                rating-=5;
                break;
        }
        if (highCard < 12 && this.pocket.getGap() <= 2) {
            rating += .5;
        }
        return rating;
    }

    public static void main(String[] args) {
        String[] input = {"NEWGAME","a","b","200","2","20","10"};
        Information test = new Information(input);
        test.pocket = new Pocket(Card.valueOf("7s"), Card.valueOf("6h"));
        System.out.println(test.evalPocket());
        test.pocket = new Pocket(Card.valueOf("As"), Card.valueOf("Kc"));
        System.out.println(test.evalPocket());
        test.pocket = new Pocket(Card.valueOf("As"), Card.valueOf("Qc"));
        System.out.println(test.evalPocket());
        test.pocket = new Pocket(Card.valueOf("Qs"), Card.valueOf("Th"));
        System.out.println(test.evalPocket());
        String[] legal = {"CHECK", "RAISE:4:100", "FOLD"};
        test.legalActions = legal;
        System.out.println(test.isLegal("RAISE"));
    }
}
