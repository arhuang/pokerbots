package pokerbots.player;

/**
 * Card class
 */
public class Card {
    public String suit;
    public int rank;

    public Card(String card){
        this.suit = card.substring(1);
        this.rank = parseRank(card.substring(0,1));
    }

    public static int parseRank(String alphaRank) {
        int rank = 0;
        try {
           rank = Integer.parseInt(alphaRank);
        } catch (Exception e) {
            switch (alphaRank){
                case ("T"):
                    rank = 10;
                    break;
                case ("J"):
                    rank = 11;
                    break;
                case ("Q"):
                    rank = 12;
                    break;
                case ("K"):
                    rank = 13;
                    break;
                case ("A"):
                    rank = 14;
                    break;
                default:
                    break;
            }
        }
        if (!(rank > 1 && rank < 15)){
            System.out.println("wrong rank");
        }
        return rank;
    }
}
