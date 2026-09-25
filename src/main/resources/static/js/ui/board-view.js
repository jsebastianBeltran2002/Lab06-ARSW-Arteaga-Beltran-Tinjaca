/**
 * BoardView — renders the current Board as SVG and turns raw mouse
 * events into semantic interaction callbacks. It never touches
 * BoardState or BoardApiClient directly: it only reads the state object
 * it is given to render() and reports interactions upward through the
 * handlers passed to the constructor. The DOM is a pure projection of
 * that state — dragging updates the screen live but the committed x/y
 * is only reported once, on mouse-up.
 */

const SVG_NS = "http://www.w3.org/2000/svg";
const DRAG_THRESHOLD_PX = 3;

function elementCenter(element) {
    return {
        x: element.x + element.width / 2,
        y: element.y + element.height / 2,
    };
}

export class BoardView {
    /**
     * @param {SVGSVGElement} svg
     * @param {{onElementInteraction:(id:string)=>void, onElementDragEnd:(id:string,x:number,y:number)=>void, onCanvasInteraction:(x:number,y:number)=>void}} handlers
     */
    constructor(svg, handlers) {
        this.svg = svg;
        this.handlers = handlers;
        this.drag = null;

        this.svg.addEventListener("mousedown", (e) => this.onMouseDown(e));
        window.addEventListener("mousemove", (e) => this.onMouseMove(e));
        window.addEventListener("mouseup", (e) => this.onMouseUp(e));
    }

    toSvgPoint(evt) {
        const rect = this.svg.getBoundingClientRect();
        const viewBox = this.svg.viewBox.baseVal;
        const scaleX = viewBox.width / rect.width;
        const scaleY = viewBox.height / rect.height;
        return {
            x: viewBox.x + (evt.clientX - rect.left) * scaleX,
            y: viewBox.y + (evt.clientY - rect.top) * scaleY,
        };
    }

    onMouseDown(evt) {
        const group = evt.target.closest("[data-element-id]");
        const point = this.toSvgPoint(evt);
        if (group) {
            const draggable = group.dataset.x !== undefined;
            this.drag = {
                elementId: group.dataset.elementId,
                startX: point.x,
                startY: point.y,
                originX: draggable ? Number(group.dataset.x) : 0,
                originY: draggable ? Number(group.dataset.y) : 0,
                draggable,
                moved: false,
                group,
            };
        } else {
            this.drag = { elementId: null, startX: point.x, startY: point.y, moved: false };
        }
    }

    onMouseMove(evt) {
        if (!this.drag) {
            return;
        }
        const point = this.toSvgPoint(evt);
        const dx = point.x - this.drag.startX;
        const dy = point.y - this.drag.startY;
        if (Math.abs(dx) > DRAG_THRESHOLD_PX || Math.abs(dy) > DRAG_THRESHOLD_PX) {
            this.drag.moved = true;
        }
        if (this.drag.moved && this.drag.draggable) {
            this.drag.group.setAttribute("transform", `translate(${dx}, ${dy})`);
        }
    }

    onMouseUp(evt) {
        if (!this.drag) {
            return;
        }
        const { elementId, moved, draggable, startX, startY, originX, originY } = this.drag;
        this.drag = null;

        if (!elementId) {
            if (!moved) {
                const point = this.toSvgPoint(evt);
                this.handlers.onCanvasInteraction(point.x, point.y);
            }
            return;
        }

        if (moved && draggable) {
            const point = this.toSvgPoint(evt);
            const dx = point.x - startX;
            const dy = point.y - startY;
            this.handlers.onElementDragEnd(elementId, originX + dx, originY + dy);
        } else {
            this.handlers.onElementInteraction(elementId);
        }
    }

    render(state) {
        while (this.svg.firstChild) {
            this.svg.removeChild(this.svg.firstChild);
        }
        if (!state.board) {
            return;
        }

        const elements = state.board.elements;
        const byId = new Map(elements.map((element) => [element.id, element]));

        for (const element of elements) {
            if (element.type === "CONNECTOR") {
                const node = this.renderConnector(element, byId, state);
                if (node) {
                    this.svg.appendChild(node);
                }
            }
        }
        for (const element of elements) {
            if (element.type === "RECTANGLE") {
                this.svg.appendChild(this.renderRectangle(element, state));
            } else if (element.type === "TEXT") {
                this.svg.appendChild(this.renderText(element, state));
            }
        }
    }

    renderRectangle(element, state) {
        const g = this.createGroup(element, state, true);

        const rect = document.createElementNS(SVG_NS, "rect");
        rect.setAttribute("x", element.x);
        rect.setAttribute("y", element.y);
        rect.setAttribute("width", element.width);
        rect.setAttribute("height", element.height);
        g.appendChild(rect);

        if (element.text) {
            g.appendChild(this.createLabel(element));
        }
        return g;
    }

    renderText(element, state) {
        const g = this.createGroup(element, state, true);
        g.classList.add("board-text");

        const background = document.createElementNS(SVG_NS, "rect");
        background.setAttribute("x", element.x);
        background.setAttribute("y", element.y);
        background.setAttribute("width", element.width);
        background.setAttribute("height", element.height);
        background.setAttribute("class", "text-background");
        g.appendChild(background);
        g.appendChild(this.createLabel(element));
        return g;
    }

    createLabel(element) {
        const label = document.createElementNS(SVG_NS, "text");
        label.setAttribute("x", element.x + element.width / 2);
        label.setAttribute("y", element.y + element.height / 2);
        label.setAttribute("text-anchor", "middle");
        label.setAttribute("dominant-baseline", "central");
        label.textContent = element.text ?? "";
        return label;
    }

    createGroup(element, state, draggable) {
        const g = document.createElementNS(SVG_NS, "g");
        g.dataset.elementId = element.id;
        if (draggable) {
            g.dataset.x = element.x;
            g.dataset.y = element.y;
        }
        g.classList.add("board-element");
        if (element.id === state.selectedElementId) {
            g.classList.add("selected");
        }
        if (element.id === state.connectSourceId) {
            g.classList.add("connect-source");
        }
        return g;
    }

    renderConnector(element, byId, state) {
        const source = byId.get(element.sourceId);
        const target = byId.get(element.targetId);
        if (!source || !target) {
            return null;
        }
        const from = elementCenter(source);
        const to = elementCenter(target);

        const g = document.createElementNS(SVG_NS, "g");
        g.dataset.elementId = element.id;
        g.classList.add("board-element", "board-connector");
        if (element.id === state.selectedElementId) {
            g.classList.add("selected");
        }

        const hitArea = document.createElementNS(SVG_NS, "line");
        hitArea.setAttribute("x1", from.x);
        hitArea.setAttribute("y1", from.y);
        hitArea.setAttribute("x2", to.x);
        hitArea.setAttribute("y2", to.y);
        hitArea.setAttribute("class", "connector-hit-area");
        g.appendChild(hitArea);

        const line = document.createElementNS(SVG_NS, "line");
        line.setAttribute("x1", from.x);
        line.setAttribute("y1", from.y);
        line.setAttribute("x2", to.x);
        line.setAttribute("y2", to.y);
        line.setAttribute("marker-end", "url(#connector-arrow)");
        g.appendChild(line);

        return g;
    }
}
