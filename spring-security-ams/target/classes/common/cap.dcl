// ----------------------------------------------------------------------------------
// Generated from a CAP model by cap2dcl version 0.7.1
// ----------------------------------------------------------------------------------

POLICY "buyer (Not restricted)" {
    GRANT buyer ON $SCOPES;
}

POLICY "approver (Not restricted)" {
    GRANT approver ON $SCOPES;
    GRANT * on "ApproverService.Orders" 
        WHERE ApproverService.Orders.requestor IS NOT RESTRICTED;
}

POLICY "autoApprove (Not restricted)" {
    GRANT autoApprove ON $SCOPES;
        GRANT * on "AutoApprove.OrderItems" 
        WHERE AutoApprove.OrderItems.amount IS NOT RESTRICTED;
}

POLICY "seller (Not restricted)" {
    GRANT seller ON $SCOPES;
}
