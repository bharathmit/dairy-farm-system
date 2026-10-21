package com.dairyfarm.animal;

/** Value lists used by animals. */
public final class AnimalEnums {

    private AnimalEnums() {
    }

    /** OTHER is for non-dairy animals (goats for meat, poultry, …). */
    public enum Category {
        CALF, HEIFER, MILKING, DRY, BULL, OTHER;

        boolean femaleOnly() {
            return this == HEIFER || this == MILKING || this == DRY;
        }

        boolean maleOnly() {
            return this == BULL;
        }
    }

    public enum Status { ACTIVE, SOLD, DEAD, TRANSFERRED }

    public enum Acquisition { BORN, PURCHASED }
}
