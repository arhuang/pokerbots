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
    public Card card1;
    public Card card2;
    public int myBank;
    public int otherBank;
    public double timeBank;
    public int potSize;
    public Card[] boardCards;
    public String[] lastActions;
    public String[] legalActions;

    /**
     * initializes information from newgame packet
     * NEWGAME yourName opp1Name stackSize bb numHands timeBank
     */
    public Information(String[] words) {
        this.yourName = words[1];
        this.oppName = words[2];
        this.myBank = Integer.parseInt(words[3]);
        this.otherBank = Integer.parseInt(words[3]);
        this.bb = Integer.parseInt(words[4]);
        this.numHands = Integer.parseInt(words[5]);
        this.timeBank = Double.parseDouble(words[6]);
    }

    public double evalHand(Card c1, Card c2) {
        double rating = 1;
        switch (Math.max(c1.rank, c2.rank)) {
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
                rating = highCard/2;
                break;
        }
        if (c1.rank == c2.rank) {
            rating *= 2;
            if (rating < 5) rating = 5;
        }
        if (c1.suit.equals(c2.suit)) {
            rating += 2;
        }
        switch (Math.abs(c1.rank - c2.rank)) {
            case 0:
                break;
            case 1:
                rating+=1;
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
        return rating;
    }

    public static void main(String[] args) {
        Card c1 = new Card("As");
        Card c2 = new Card("Ac");
        Card c3 = new Card("Ks");
        Card c4 = new Card("9s");
        System.out.println(evalHand(c1,c2));
        System.out.pritnln(evalHand(c3,c4));
    }
}
