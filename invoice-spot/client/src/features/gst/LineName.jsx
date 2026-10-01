// A line's or item's name, with its HSN or SAC code under it when it has one.
const LineName = ({ name, hsnSac }) => (
  <>
    {name}
    {hsnSac && (
      <span className="mt-0.5 block text-[12px] font-normal text-spot-muted">
        HSN/SAC {hsnSac}
      </span>
    )}
  </>
);

export default LineName;
