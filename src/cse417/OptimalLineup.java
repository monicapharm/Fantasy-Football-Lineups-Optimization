package cse417;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils.Collections;

import javafx.util.Pair;

/**
 * Program to find the lineup that has the highest projected points, subject to
 * constraints on the total budget and number at each position.
 */
public class OptimalLineup {

  /** Maximum that can be spent on players in a lineup. */
    private static final int BUDGET = 60000;
    private static final float MIN_VALUE = -6000000;

  // Number of players that must be played at each position:
  private static final int NUM_QB = 1;
  private static final int NUM_RB = 2;
  private static final int NUM_WR = 3;
  private static final int NUM_TE = 1;
  private static final int NUM_K = 1;
  private static final int NUM_DEF = 1;
  private static List<Player> players;
  private static int[] a = {(BUDGET/100+1)*(NUM_QB+1)*(NUM_RB+1)*(NUM_WR+1)*(NUM_TE+1)*(NUM_K+1), (BUDGET/100+1)*(NUM_QB+1)*(NUM_RB+1)*(NUM_WR+1)*(NUM_TE+1), 
          (BUDGET/100+1)*(NUM_QB+1)*(NUM_RB+1)*(NUM_WR+1), (BUDGET/100+1)*(NUM_QB+1)*(NUM_RB+1), (BUDGET/100+1)*(NUM_QB+1), (BUDGET/100+1)};
  private static float[] dpList = new float[62000000]; // to record the value of each sub-problem 
  private static boolean[] isIChosen = new boolean[62000000]; // to record whether the ith player is chosen   
  private static int size;
  
  /** Entry point for a program to compute optimal lineups. */
  public static void main(String[] args) throws Exception {
    ArgParser argParser = new ArgParser("OptimalLineup");
    argParser.addOption("no-high-correlations", Boolean.class);
    args = argParser.parseArgs(args, 1, 1);

    // Parse the list of players from the file given in args[0]
    players = new ArrayList<Player>();
    CsvParser parser = new CsvParser(args[0], false, new Object[] {
          // name, position, team, opponent
          String.class, String.class, String.class, String.class,
          // points, price, floor, ceiling, stddev
          Float.class, Integer.class, Float.class, Float.class, Float.class
        });
    while (parser.hasNext()) {
      String[] row = parser.next();
      players.add(new Player(row[0], Position.valueOf(row[1]), row[2], row[3],
          Integer.parseInt(row[5]), Float.parseFloat(row[4]),
          Float.parseFloat(row[8])));
    }
    
    for (int i = 0; i < 62000000; i++) {
      dpList[i] = MIN_VALUE;
    }

    List<Player> roster;
    if (argParser.hasOption("no-high-correlations")) {
        roster = findOptimalLineupWithoutHighCorrelations(players, "");
    } else {
      roster = findOptimalLineup(players);
    }

    displayLineup(roster);
  }
  
private static int hashInt(int i, int budget, int num_QB, int num_RB, int num_WR, int num_TE, int num_K, int num_DEF) { // hash each sub-problem's parameters into distinct index
    int budget_simple = budget/100;
    int index = (size + 1) * (a[0] * num_DEF + a[1] * num_K + a[2] * num_TE + a[3] * num_WR + a[4] * num_RB + a[5] * num_QB + budget_simple) + i + 1;     
    return index;
}

private static float dp(int i, int budget, int num_QB, int num_RB, int num_WR, int num_TE, int num_K, int num_DEF) { // find the largest value we can get 
    if (budget < 0 || num_QB < 0 || num_RB < 0 || num_WR < 0 || num_TE < 0 || num_K < 0 || num_DEF < 0) {
        return MIN_VALUE;
    }
    int curtIndex = hashInt(i, budget, num_QB, num_RB, num_WR, num_TE, num_K, num_DEF);

    int numPlayer = num_QB + num_RB + num_WR + num_TE + num_K + num_DEF;
    if (budget >=0 && numPlayer == 0) {
        dpList[curtIndex] = 0f;    
        return 0f;
    }
    if (i + 1 < numPlayer) {
        return MIN_VALUE;
    }
    
    Player player = players.get(i);
    
    if (budget - player.getPrice() < 0) {
        return MIN_VALUE; 
    }
    
    if (dpList[curtIndex] >= 0.0f) {
        return dpList[curtIndex];
    }
    float v1 = dp(i - 1, budget, num_QB, num_RB, num_WR, num_TE, num_K, num_DEF); // the ith is not chosen
    
    float v2= dp(i - 1, budget - player.getPrice(), num_QB - player.atPositionInt(Position.QB), num_RB - player.atPositionInt(Position.RB), 
            num_WR - player.atPositionInt(Position.WR), num_TE - player.atPositionInt(Position.TE), 
            num_K - player.atPositionInt(Position.K), num_DEF - player.atPositionInt(Position.DEF)) + player.getPointsExpected(); // the ith is chosen
    float v = Math.max(v1, v2); 
    
    if (v < 0) {
        return MIN_VALUE;
    }
    if (v1 < v2) {        
        isIChosen[curtIndex] = true;
    } else {
        isIChosen[curtIndex] = false;
    }
    dpList[curtIndex] = v;
    return v;

}

private static List<Player> printPlayer(int i, int budget, int num_QB, int num_RB, int num_WR, int num_TE, int num_K, int num_DEF, List<Player> allPlayers, List<Player> playerList) {
    if (i < 0) {
        return playerList;
    }
    int getIndex = hashInt(i, budget, num_QB, num_RB, num_WR, num_TE, num_K, num_DEF);
    
    if (isIChosen[getIndex]) { // the ith is chosen
        Player playerChosen = allPlayers.get(i);
        playerList.add(playerChosen);
        printPlayer(i - 1, budget - playerChosen.getPrice(), num_QB - playerChosen.atPositionInt(Position.QB), num_RB - playerChosen.atPositionInt(Position.RB), 
                num_WR - playerChosen.atPositionInt(Position.WR), num_TE - playerChosen.atPositionInt(Position.TE), 
                num_K - playerChosen.atPositionInt(Position.K), num_DEF - playerChosen.atPositionInt(Position.DEF), allPlayers, playerList);
    } else {  // the ith is not chosen
        printPlayer(i - 1, budget, num_QB, num_RB, num_WR, num_TE, num_K, num_DEF, allPlayers, playerList);
    }   
    return playerList;
}

/** Returns the players in the optimal lineup (in any order). */
private static List<Player> findOptimalLineup(List<Player> allPlayers) {
    return findOptimalLineupWithValue(allPlayers).getKey();
}

private static Pair<List<Player>, Float> findOptimalLineupWithValue(List<Player> allPlayers) {
    size = allPlayers.size() - 1;
    //System.out.println("size="+size);
    float value = dp(size, BUDGET, NUM_QB, NUM_RB, NUM_WR, NUM_TE, NUM_K, NUM_DEF);
    List<Player> playerList = new ArrayList<>();
    printPlayer(size, BUDGET, NUM_QB, NUM_RB, NUM_WR, NUM_TE, NUM_K, NUM_DEF, allPlayers, playerList);
    return new Pair<List<Player>, Float>(playerList, value);
}

  /**
   * Returns the players in the optimal lineup subject to the constraint that
   * there are no players with high correlations, i.e., no QB-WR, QB-K, or
   * K-DEF from the same team.
   */
  private static List<Player> findOptimalLineupWithoutHighCorrelations(
      List<Player> allPlayers, String label) {
      return findOptimalLineupWithoutHighCorrelations(allPlayers).getKey();
  }

  private static Pair<List<Player>, Float> findOptimalLineupWithoutHighCorrelations(
      List<Player> allPlayers) {
      Pair<List<Player>, Float> res = findOptimalLineupWithValue(allPlayers);
      Player[] correlatedPlayers = getHighCorrelations(res.getKey());
      if (correlatedPlayers == null) {
          return res;
      }
      
      int idx0 = allPlayers.indexOf(correlatedPlayers[0]);
      int idx1 = allPlayers.indexOf(correlatedPlayers[1]);

      List<Player> allPlayersCopy0 = new ArrayList<Player>(allPlayers);
      allPlayersCopy0.remove(idx0);
      Pair<List<Player>, Float> res0 = findOptimalLineupWithoutHighCorrelations(allPlayersCopy0);

      List<Player> allPlayersCopy1 = new ArrayList<Player>(allPlayers);
      allPlayersCopy0.remove(idx1);
      Pair<List<Player>, Float> res1 = findOptimalLineupWithoutHighCorrelations(allPlayersCopy1);
      
      return res0.getValue() > res1.getValue() ? res0 : res1;
  }

  /** Returns a pair that are highly correlated or null if none. */
  private static Player[] getHighCorrelations(List<Player> roster) {
    Player qb = roster.stream()
        .filter(p -> p.getPosition() == Position.QB).findFirst().get();

    List<Player> wrs = roster.stream()
        .filter(p -> p.getPosition() == Position.WR)
        .sorted((p,q) -> q.getPrice() - p.getPrice())
        .collect(Collectors.toList());
    for (Player wr : wrs) {
      if (qb.getTeam().equals(wr.getTeam()))
        return new Player[] { qb, wr };
    }

    Player k = roster.stream()
        .filter(p -> p.getPosition() == Position.K).findFirst().get();
    if (qb.getTeam().equals(k.getTeam()))
      return new Player[] { qb, k };

    Player def = roster.stream()
        .filter(p -> p.getPosition() == Position.DEF).findFirst().get();
    if (k.getTeam().equals(def.getTeam()))
      return new Player[] { k, def };

    return null;
  }

  /** Displays a lineup, which is assumed to meet the position constraints. */
  private static void displayLineup(List<Player> roster) {
    if (roster == null) {
      System.out.println("*** No solution");
      return;
    }

    List<Player> qbs = roster.stream()
        .filter(p -> p.getPosition() == Position.QB)
        .collect(Collectors.toList());
    List<Player> rbs = roster.stream()
        .filter(p -> p.getPosition() == Position.RB)
        .sorted((p,q) -> q.getPrice() - p.getPrice())
        .collect(Collectors.toList());
    List<Player> wrs = roster.stream()
        .filter(p -> p.getPosition() == Position.WR)
        .sorted((p,q) -> q.getPrice() - p.getPrice())
        .collect(Collectors.toList());
    List<Player> tes = roster.stream()
        .filter(p -> p.getPosition() == Position.TE)
        .collect(Collectors.toList());
    List<Player> ks = roster.stream()
        .filter(p -> p.getPosition() == Position.K)
        .collect(Collectors.toList());
    List<Player> defs = roster.stream()
        .filter(p -> p.getPosition() == Position.DEF)
        .collect(Collectors.toList());

    assert qbs.size() == NUM_QB;
    assert rbs.size() == NUM_RB;
    assert wrs.size() == NUM_WR;
    assert tes.size() == NUM_TE;
    assert ks.size() == NUM_K;
    assert defs.size() == NUM_DEF;

    assert roster.stream().mapToInt(p -> p.getPrice()).sum() <= BUDGET;

    System.out.printf(" QB  %s\n", describePlayer(qbs.get(0)));
    System.out.printf("RB1  %s\n", describePlayer(rbs.get(0)));
    System.out.printf("RB2  %s\n", describePlayer(rbs.get(1)));
    System.out.printf("WR1  %s\n", describePlayer(wrs.get(0)));
    System.out.printf("WR2  %s\n", describePlayer(wrs.get(1)));
    System.out.printf("WR3  %s\n", describePlayer(wrs.get(2)));
    System.out.printf(" TE  %s\n", describePlayer(tes.get(0)));
    System.out.printf("  K  %s\n", describePlayer(ks.get(0)));
    System.out.printf("DEF  %s\n", describePlayer(defs.get(0)));
    System.out.printf("*** Totals: price $%d, points %.1f +/- %.1f\n",
        roster.stream().mapToInt(p -> p.getPrice()).sum(),
        roster.stream().mapToDouble(p -> p.getPointsExpected()).sum(),
        Math.sqrt(roster.stream().mapToDouble(
            p -> p.getPointsVariance()).sum()));
  }

  /** Returns a short description of a player with price and opponent. */
  private static String describePlayer(Player p) {
    return String.format("%-20s $%-5d %3s %2s %3s", p.getName(), p.getPrice(),
        p.getTeam(), p.isAtHome() ? "vs" : "at", p.getOpponent());
  }
}
