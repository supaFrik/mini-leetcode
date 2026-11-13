public class AgeValidator {
    private AgeValidator() {
    }

    public static int validate(String raw) throws InvalidAgeException {
        int age;
        try {
            age = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new InvalidAgeException("Không phải số: " + raw, e);
        }
        if (age < 0 || age > 150) {
            throw new InvalidAgeException("Tuổi không hợp lệ: " + age);
        }
        return age;
    }
}
