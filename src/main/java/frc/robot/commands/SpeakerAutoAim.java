package frc.robot.commands;

import java.util.function.DoubleSupplier;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Transform3d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.Constants;
import frc.robot.subsystems.Kinesthetics;
import frc.robot.subsystems.Shooter;
import frc.robot.subsystems.Swerve;

public class SpeakerAutoAim extends Command {
    private Kinesthetics kinesthetics;
    private Swerve s_Swerve;
    private Shooter s_Shooter;

    private DoubleSupplier desiredTranslation;
    private DoubleSupplier desiredStrafe;
    private double desiredYaw;
    private double desiredPitch;
    private double desiredNoteVel; // radians/second

    public SpeakerAutoAim(Kinesthetics k, Swerve sw, Shooter sh, DoubleSupplier translationSup, DoubleSupplier strafeSup) {
        kinesthetics = k;
        s_Swerve = sw;
        s_Shooter = sh;
        desiredTranslation = translationSup;
        desiredStrafe = strafeSup;
        addRequirements(s_Swerve, s_Shooter);
    }

    private static Transform3d getDifference(Kinesthetics k) {
        return new Pose3d(k.getPose()).minus(Constants.Environment.speakers.get(k.getAlliance()));
    }

    @Override
    public void execute() {
        double translationVal = MathUtil.applyDeadband(desiredTranslation.getAsDouble(), Constants.stickDeadband);
        double strafeVal = MathUtil.applyDeadband(desiredStrafe.getAsDouble(), Constants.stickDeadband);

        Transform3d transform = getDifference(kinesthetics);

        double roottwoh = Math.sqrt(2*transform.getZ()); // Z, up +
        double rootg = Math.sqrt(Constants.Environment.G);

        double relativex = Math.abs(transform.getY()); // Y
        double relativey = (transform.getY() > 0 ? -1 : 1) * Math.abs(transform.getX());
        double relativexv = (transform.getY() > 0 ? 1 : -1) * Math.abs(kinesthetics.getVelocity().get(1, 0));
        double relativeyv = (transform.getY() > 0 ? -1 : 1) * Math.abs(kinesthetics.getVelocity().get(0, 0));
        
        double n = (rootg * relativex) / roottwoh - relativexv;
        double m = Math.pow(p * rootg / roottwoh + relativeyv, 2);

        desiredPitch = Math.atan((roottwoh * rootg) / n);
        if (desiredPitch < 0) desiredPitch += Math.PI;
        desiredYaw = Math.atan(((rootg * p) / roottwoh + relativeyv) / n);
        desiredNoteVel = Math.sqrt(
            2*Constants.Environment.G*transform.getZ()
            + n*n
            + m
        ) + Math.sqrt(
            Math.sqrt(
                b * 54481/300000 *
                (
                    2 * Constants.Environment.G * transform.getZ() +
                    n * n +
                    m
                ) *
                Math.sqrt(transform.getZ()*transform.getZ() + relativex * relativex + p * p)
            ) * 400/47
        );

        s_Swerve.drive(
            new Translation2d(translationVal, strafeVal).times(Constants.Swerve.maxSpeed),
            desiredYaw, true, false
        );
        s_Shooter.setGoalPitch(desiredPitch);
        s_Shooter.setGoalSpin(desiredNoteVel);
    }

    @Override
    public boolean isFinished() {
        return Math.abs(kinesthetics.getHeading().getRadians() - desiredYaw) < Constants.Swerve.yawTolerance
            && Math.abs(s_Shooter.getPitch() - desiredPitch) < Constants.Shooter.pitchTolerance
            && Math.abs(s_Shooter.getSpin() - desiredNoteVel) < Constants.Shooter.spinTolerance
            && kinesthetics.getVelocity().get(2, 0) < Constants.CommandConstants.speakerAutoOmegaMax;
    }

    @Override
    public void end(boolean interrupted) {
        if (interrupted) {
            s_Shooter.setGoalPitch(0);
            s_Shooter.stopSpin();
        }
        super.end(interrupted);
    }

    public static boolean isInRange(Kinesthetics k) {
        Translation2d offset = getDifference(k).getTranslation().toTranslation2d();
        return Math.abs(offset.getY()) < 5;
    }
}
