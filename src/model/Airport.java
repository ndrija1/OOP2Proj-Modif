package model;

/**
 * Immutable airport. The CONSTRUCTOR enforces every rule (name non-empty,
 * code = 3 uppercase letters, x in [-180,180], y in [-90,90]) - an invalid
 * Airport cannot exist. Code uniqueness is checked by {@link World}, because
 * a single airport cannot know about the others.
 */
public final class Airport {

    public static final double MIN_X = -180, MAX_X = 180;
    public static final double MIN_Y = -90, MAX_Y = 90;
    public static final String CODE_PATTERN = "[A-Z]{3}";

    private final String name;
    private final String code;
    private final double x;
    private final double y;

    public Airport(String name, String code, double x, double y) throws ValidationException {

       if(name!=null) this.name=name.trim();
       else this.name = "";
       if(code!=null) this.code=code.trim();
       else this.code = "";
       if(name.isEmpty()){
            throw new ValidationException("Airport name cannot be empty");
        }
       if(code.isEmpty()){
            throw new ValidationException("Airport code cannot be empty");
       }
       if(!code.matches(CODE_PATTERN)){
           throw new ValidationException("Airport code does not match expected format");
       }
       if(x<MIN_X || x>MAX_X){
           throw new ValidationException("Airport x out of bounds");
       }
       if(y<MIN_Y || y>MAX_Y){
           throw new ValidationException("Airport y out of bounds");
       }


        this.x = x;
        this.y = y;
    }

    public String getName() { return name; }
    public String getCode() { return code; }
    public double getX()    { return x; }
    public double getY()    { return y; }

    @Override
    public String toString() {
        return code + " (" + name + ", " + x + ", " + y + ")";
    }
}
