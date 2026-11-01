package fr.sukikui.biomemap.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BiomeMapArgumentsTest {

  @ParameterizedTest
  @CsvSource({"1,4", "4,4", "5,8", "8,8", "9,16", "16,16", "17,32", "33,48"})
  void preservesResolutionAlignment(int requested, int expected) {
    assertEquals(expected, BiomeMapArguments.alignCellSize(requested));
  }

  @Test
  void rejectsCellSizesThatCannotBeRoundedWithoutOverflow() {
    assertEquals(2_147_483_632, BiomeMapArguments.alignCellSize(2_147_483_632));
    assertThrows(IllegalArgumentException.class,
        () -> BiomeMapArguments.alignCellSize(Integer.MAX_VALUE));
  }
}
