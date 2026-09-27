package net.tfminecraft.simplefactions.government.proposal;

import java.util.ArrayList;
import java.util.List;

import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.simplefactions.objects.handler.VehicleFeeHandler;
import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

/** The lines that describe a fee proposal in books, menus and session reports. */
public final class FeeProposalText {
    private FeeProposalText() {}

    public static String target(FeeChange fee) {
        return fee.isGeneral() ? "All vehicles" : fee.getVehicleTypeId();
    }

    public static List<String> lines(Faction f, FeeChange fee) {
        List<String> lines = new ArrayList<>();
        FeeKind kind = fee.getKind();
        lines.add(StringFormatter.formatHex("#b8ae61Fee: #c2bea7" + kind.getDisplayName()));
        lines.add(StringFormatter.formatHex("#b8ae61Vehicle: #c2bea7" + target(fee)));
        if (f == null) {
            lines.add(StringFormatter.formatHex("#b8ae61Change: #c2bea7" + kind.formatRate(fee.getNewRate())));
            return lines;
        }
        VehicleFeeHandler handler = f.getVehicleFeeHandler();
        double old = handler.getRate(kind, fee.getVehicleTypeId());
        lines.add(StringFormatter.formatHex("#b8ae61Change: #c2bea7" + kind.formatRate(old)
                + " §7-> #c2bea7" + kind.formatRate(fee.getNewRate())));
        if (!fee.isGeneral()) {
            lines.add(StringFormatter.formatHex("#3f4040(#767a77General Rate: #928d7a"
                    + kind.formatRate(handler.getRate(kind)) + "#3f4040)"));
        }
        return lines;
    }
}
