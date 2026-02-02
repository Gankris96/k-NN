import org.opensearch.core.common.unit.ByteSizeValue;
import org.opensearch.core.common.unit.ByteSizeUnit;
import org.opensearch.knn.index.KNNSettings;

public class TestSettingValidation {
    public static void main(String[] args) {
        try {
            // Test that the enforced minimum is set correctly
            ByteSizeValue enforcedMin = KNNSettings.KNN_REMOTE_INDEX_BUILD_ENFORCED_MIN_VALUE;
            System.out.println("Enforced minimum value: " + enforcedMin.getStringRep());
            
            // Test that it equals 10MB
            ByteSizeValue expectedMin = new ByteSizeValue(10, ByteSizeUnit.MB);
            if (enforcedMin.equals(expectedMin)) {
                System.out.println("✓ Enforced minimum is correctly set to 10MB");
            } else {
                System.out.println("✗ Enforced minimum is not 10MB, got: " + enforcedMin.getStringRep());
            }
            
            System.out.println("Setting validation test completed successfully!");
        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}