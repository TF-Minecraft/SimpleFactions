package net.tfminecraft.simplefactions.tiers;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Higher titles take one connected piece: free titles whose land touches, plus untitled land touching them. */
class TitleLandTest {
  // A line of land 1-2-3-4-5-6; 7 and 8 sit apart, 8 faces 6 across river tile 50, 7 touches nothing.
  private static final Map<Integer, List<Integer>> BORDERS =
      Map.of(1, List.of(2), 2, List.of(1, 3), 3, List.of(2, 4), 4, List.of(3, 5), 5, List.of(4, 6), 6, List.of(5));
  private static final Map<Integer, List<Integer>> RIVERS = Map.of(6, List.of(50), 8, List.of(50));
  private static final CountyLand.Ties TIES =
      new CountyLand.Ties(id -> BORDERS.getOrDefault(id, List.of()), id -> RIVERS.getOrDefault(id, List.of()));
  private static final Function<Title, Collection<Integer>> LAND = Title::getProvinces;

  @Test
  void touchingTitlesFormOnePieceWithTheUntitledLandBetweenAndBesideThem() {
    Title west = county("west", 1, 2), east = county("east", 4, 5), apart = county("apart", 7);
    List<Title> free = List.of(west, east, apart);
    // 3 bridges the two counties, 6 touches the east county; 7's county is cut off.
    TitleLand.Piece piece = TitleLand.pieceAt(1, free, List.of(3, 6), LAND, TIES);
    assertEquals(List.of(west, east), piece.titles());
    assertEquals(List.of(3, 6), piece.provinces());
    assertEquals(List.of(apart), TitleLand.pieceAt(7, free, List.of(3, 6), LAND, TIES).titles());
    assertEquals(2, TitleLand.largestPiece(free, List.of(3, 6), LAND, TIES));
  }

  @Test
  void titledLandBetweenTwoTitlesKeepsThemApart() {
    Title west = county("west", 1, 2), east = county("east", 4, 5);
    // 3 is neither free nor untitled (another title holds it), so nothing joins west and east.
    TitleLand.Piece piece = TitleLand.pieceAt(2, List.of(west, east), List.of(), LAND, TIES);
    assertEquals(List.of(west), piece.titles());
    assertEquals(1, TitleLand.largestPiece(List.of(west, east), List.of(), LAND, TIES));
  }

  @Test
  void aRiverJoinsTitlesButStandingOutsideOrInLoneUntitledLandTakesNothing() {
    Title shore = county("shore", 6), across = county("across", 8);
    List<Title> free = List.of(shore, across);
    assertEquals(List.of(shore, across), TitleLand.pieceAt(8, free, List.of(), LAND, TIES).titles());
    assertSame(TitleLand.Piece.EMPTY, TitleLand.pieceAt(99, free, List.of(), LAND, TIES));
    assertSame(TitleLand.Piece.EMPTY, TitleLand.pieceAt(7, free, List.of(7), LAND, TIES), "Untitled land touching no title");
    assertEquals(0, TitleLand.largestPiece(List.of(), List.of(7), LAND, TIES));
  }

  @Test
  void aTitleAlreadySplitInTwoJoinsEitherSideButCountsOnce() {
    Title split = county("split", 1, 6), middle = county("middle", 3), landless = county("landless");
    List<Title> free = List.of(split, middle, landless);
    assertEquals(List.of(split, middle), TitleLand.pieceAt(6, free, List.of(2), LAND, TIES).titles());
    assertEquals(List.of(split), TitleLand.pieceAt(6, free, List.of(), LAND, TIES).titles());
  }

  private static Title county(String id, Integer... provinces) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("tier", 2);
    yaml.set("form-cost", 1);
    JsonObject json = new JsonObject();
    json.addProperty("name", id);
    JsonArray land = new JsonArray();
    for (int province : provinces) land.add(province);
    json.add("provinces", land);
    return new Title(new Tier("county", yaml), id, json);
  }
}
